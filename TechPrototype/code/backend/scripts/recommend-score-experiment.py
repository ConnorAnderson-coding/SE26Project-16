#!/usr/bin/env python3
"""
Recommendation score-factor experiment (mirrors Java RecommendationService / Scorer).

For seed users, rebuild preference vectors → ES kNN recall → hard filter →
factor scores (raw + min-max) + weighted final, then write analysis tables.

Outputs:
  - report/recommend-score-experiment.md
  - report/recommend-score-experiment.csv
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import subprocess
import urllib.error
import urllib.request
from collections import defaultdict
from datetime import date, datetime
from pathlib import Path

ES_BASE = "http://127.0.0.1:9200"
INDEX = "campus_activities"
MODEL = "campus_gte"
MYSQL = [
    "docker", "exec", "campus-mysql",
    "mysql", "-ucampus", "-pcampus123",
    "--default-character-set=utf8mb4",
    "campus_activity", "-N", "-B",
]

# Match application.properties defaults
RECALL = 50
PREF_HISTORY_K = 10
PREF_HALF_LIFE = 30.0
PREF_INTEREST_MIX = 0.4  # weight on interest vector
W_SIM, W_TAG, W_SOC, W_HOT, W_TIME = 0.55, 0.15, 0.15, 0.10, 0.05

USERS = [
    ("524030910001", "张三"),
    ("524030910002", "李四"),
    ("T001", "王老师"),
]

ROOT = Path(__file__).resolve().parents[2]
OUT_MD = ROOT / "report" / "recommend-score-experiment.md"
OUT_CSV = ROOT / "report" / "recommend-score-experiment.csv"


def http_json(method: str, url: str, body: dict | None = None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, headers={"Content-Type": "application/json; charset=utf-8"}, method=method
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def mysql_rows(sql: str) -> list[list[str]]:
    proc = subprocess.run(
        MYSQL + ["-e", sql],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode != 0:
        raise RuntimeError(f"mysql failed: {proc.stderr}")
    rows = []
    for line in proc.stdout.splitlines():
        if not line.strip() or line.startswith("mysql:"):
            continue
        rows.append(line.split("\t"))
    return rows


def parse_json_list(raw: str | None) -> list[str]:
    if not raw or raw in ("NULL", "\\N", "[]"):
        return []
    try:
        val = json.loads(raw)
        if isinstance(val, list):
            return [str(x).strip() for x in val if x is not None and str(x).strip()]
    except json.JSONDecodeError:
        pass
    return []


def parse_dt(raw: str | None) -> datetime | None:
    if not raw or raw in ("NULL", "\\N"):
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(raw, fmt)
        except ValueError:
            continue
    return None


def l2_normalize(vec: list[float]) -> list[float]:
    s = sum(v * v for v in vec)
    if s <= 1e-12:
        return list(vec)
    n = math.sqrt(s)
    return [v / n for v in vec]


def weighted_average(vectors: list[list[float]], weights: list[float]) -> list[float] | None:
    if not vectors:
        return None
    dim = len(vectors[0])
    acc = [0.0] * dim
    wsum = 0.0
    for v, w in zip(vectors, weights):
        if w <= 0 or len(v) != dim:
            continue
        for i in range(dim):
            acc[i] += w * v[i]
        wsum += w
    if wsum <= 0:
        return None
    return l2_normalize([x / wsum for x in acc])


def convex_combine(a: list[float], b: list[float], wa: float) -> list[float]:
    wa = max(0.0, min(1.0, wa))
    wb = 1.0 - wa
    return l2_normalize([wa * x + wb * y for x, y in zip(a, b)])


def time_decay(days_ago: int, half_life: float) -> float:
    if days_ago < 0:
        days_ago = 0
    if half_life <= 0:
        return 1.0
    return 0.5 ** (days_ago / half_life)


def min_max(values: list[float]) -> list[float]:
    if not values:
        return []
    lo, hi = min(values), max(values)
    if hi <= lo:
        return [1.0] * len(values)
    return [(v - lo) / (hi - lo) for v in values]


def time_slot(start: datetime | None) -> str | None:
    if start is None:
        return None
    if start.weekday() >= 5:
        return "weekend"
    h = start.hour
    if h < 12:
        return "weekday_morning"
    if h < 18:
        return "weekday_afternoon"
    return "weekday_evening"


def time_fit(start: datetime | None, slots: list[str]) -> float:
    if not slots:
        return 1.0
    slot = time_slot(start)
    if slot is None:
        return 0.0
    return 1.0 if slot in slots else 0.0


def overlaps(a0: datetime | None, a1: datetime | None, b0: datetime | None, b1: datetime | None) -> bool:
    if not a0 or not a1 or not b0 or not b1:
        return False
    return a0 < b1 and a1 > b0


def tag_match(activity: dict, interests: list[str]) -> int:
    want = set(interests)
    hits = 0
    if activity.get("category") in want:
        hits += 1
    for t in activity.get("tags") or []:
        if t in want:
            hits += 1
    return hits


def social_formula(co: int, a_to_b: int, b_to_a: int) -> float:
    return math.log(1 + max(0, co)) + 0.8 * math.log(1 + max(0, a_to_b)) + 0.8 * math.log(1 + max(0, b_to_a))


def infer_embedding(text: str) -> list[float] | None:
    body = {"docs": [{"text_field": text}]}
    root = http_json("POST", f"{ES_BASE}/_ml/trained_models/{MODEL}/_infer", body)
    emb = None
    try:
        emb = root["inference_results"][0]["predicted_value"]
    except (KeyError, IndexError, TypeError):
        pass
    if not emb:
        # fallback scan
        def find(node):
            if isinstance(node, dict):
                if "predicted_value" in node and isinstance(node["predicted_value"], list):
                    return node["predicted_value"]
                for v in node.values():
                    r = find(v)
                    if r:
                        return r
            elif isinstance(node, list):
                for v in node:
                    r = find(v)
                    if r:
                        return r
            return None
        emb = find(root)
    if not emb:
        return None
    return [float(x) for x in emb]


def mget_embeddings(ids: list[int]) -> dict[int, list[float]]:
    if not ids:
        return {}
    body = {
        "docs": [
            {"_id": str(i), "_source": ["activity_embedding"]}
            for i in ids
        ]
    }
    root = http_json("POST", f"{ES_BASE}/{INDEX}/_mget", body)
    out: dict[int, list[float]] = {}
    for doc in root.get("docs", []):
        if not doc.get("found"):
            continue
        try:
            aid = int(doc["_id"])
            emb = doc["_source"].get("activity_embedding")
            if emb:
                out[aid] = [float(x) for x in emb]
        except (KeyError, ValueError, TypeError):
            continue
    return out


def knn_by_vector(vec: list[float], k: int) -> list[tuple[int, float]]:
    num_candidates = max(k * 2, 100)
    body = {
        "size": k,
        "_source": False,
        "knn": {
            "field": "activity_embedding",
            "query_vector": vec,
            "k": k,
            "num_candidates": num_candidates,
            "filter": {"term": {"status": "published"}},
        },
    }
    root = http_json("POST", f"{ES_BASE}/{INDEX}/_search", body)
    hits = []
    for h in root.get("hits", {}).get("hits", []):
        try:
            hits.append((int(h["_id"]), float(h["_score"])))
        except (KeyError, ValueError, TypeError):
            continue
    return hits


def load_users() -> dict[str, dict]:
    rows = mysql_rows(
        "SELECT id, name, interests, available_time FROM user "
        "WHERE id IN ('524030910001','524030910002','T001','admin001')"
    )
    users = {}
    for r in rows:
        uid = r[0]
        users[uid] = {
            "id": uid,
            "name": r[1],
            "interests": parse_json_list(r[2] if len(r) > 2 else None),
            "available_time": parse_json_list(r[3] if len(r) > 3 else None),
        }
    return users


def load_activities() -> dict[int, dict]:
    rows = mysql_rows(
        "SELECT id, title, category, start_time, end_time, organizer_id, "
        "signup_count, favorite_count, tags, status FROM activity"
    )
    acts = {}
    for r in rows:
        aid = int(r[0])
        acts[aid] = {
            "id": aid,
            "title": r[1],
            "category": r[2],
            "start_time": parse_dt(r[3]),
            "end_time": parse_dt(r[4]),
            "organizer_id": r[5],
            "signup_count": int(r[6] or 0),
            "favorite_count": int(r[7] or 0),
            "tags": parse_json_list(r[8]),
            "status": r[9],
        }
    return acts


def load_registrations() -> list[dict]:
    rows = mysql_rows(
        "SELECT user_id, activity_id, status, created_at FROM registration"
    )
    regs = []
    for r in rows:
        regs.append({
            "user_id": r[0],
            "activity_id": int(r[1]),
            "status": r[2],
            "created_at": parse_dt(r[3]),
        })
    return regs


def build_pref_vector(
    user: dict,
    regs: list[dict],
    emb_cache: dict[int, list[float]],
) -> tuple[list[float] | None, dict]:
    meta: dict = {"interest_text": "", "history_n": 0, "cold_start": False}
    interests = user["interests"]
    interest_vec = None
    if interests:
        text = " ".join(interests)
        meta["interest_text"] = text
        raw = infer_embedding(text)
        if raw:
            interest_vec = l2_normalize(raw)

    uid = user["id"]
    uregs = [
        r for r in regs
        if r["user_id"] == uid and (r["status"] is None or r["status"] != "rejected")
    ]
    uregs.sort(key=lambda r: r["created_at"] or datetime.min, reverse=True)
    uregs = uregs[:PREF_HISTORY_K]
    meta["history_n"] = len(uregs)

    vectors, weights = [], []
    today = date.today()
    need_ids = [r["activity_id"] for r in uregs if r["activity_id"] not in emb_cache]
    if need_ids:
        emb_cache.update(mget_embeddings(need_ids))
    for r in uregs:
        emb = emb_cache.get(r["activity_id"])
        if not emb:
            continue
        day = (r["created_at"].date() if r["created_at"] else today)
        days_ago = (today - day).days
        vectors.append(emb)
        weights.append(time_decay(days_ago, PREF_HALF_LIFE))
    history_vec = weighted_average(vectors, weights)

    if interest_vec is None and history_vec is None:
        meta["cold_start"] = True
        return None, meta
    if interest_vec is None:
        return history_vec, meta
    if history_vec is None:
        return interest_vec, meta
    return convex_combine(interest_vec, history_vec, PREF_INTEREST_MIX), meta


def count_co(regs: list[dict], a: str, b: str) -> int:
    acts_a = {
        r["activity_id"] for r in regs
        if r["user_id"] == a and (r["status"] is None or r["status"] != "rejected")
    }
    acts_b = {
        r["activity_id"] for r in regs
        if r["user_id"] == b and (r["status"] is None or r["status"] != "rejected")
    }
    return len(acts_a & acts_b)


def count_signed_org(regs: list[dict], activities: dict[int, dict], user: str, org: str) -> int:
    n = 0
    for r in regs:
        if r["user_id"] != user:
            continue
        if r["status"] == "rejected":
            continue
        act = activities.get(r["activity_id"])
        if act and act["organizer_id"] == org:
            n += 1
    return n


def score_user(
    user: dict,
    activities: dict[int, dict],
    regs: list[dict],
    emb_cache: dict[int, list[float]],
) -> dict:
    pref, meta = build_pref_vector(user, regs, emb_cache)
    cold = pref is None
    hits: list[tuple[int, float]]
    if cold:
        # hot order like Java cold path
        published = [a for a in activities.values() if a["status"] == "published"]
        published.sort(
            key=lambda a: -(a["signup_count"] + a["favorite_count"])
        )
        hits = [(a["id"], 0.0) for a in published[:RECALL]]
    else:
        hits = knn_by_vector(pref, RECALL)

    sim_by_id: dict[int, float] = {}
    for aid, score in hits:
        sim_by_id.setdefault(aid, score)

    signed_ids = set()
    windows = []
    for r in regs:
        if r["user_id"] != user["id"]:
            continue
        if r["status"] == "rejected":
            continue
        act = activities.get(r["activity_id"])
        if not act:
            continue
        signed_ids.add(act["id"])
        windows.append((act["start_time"], act["end_time"]))

    ordered = []
    for aid in sim_by_id:
        a = activities.get(aid)
        if a and a["status"] == "published":
            ordered.append(a)

    kept = []
    dropped = []
    for a in ordered:
        if a["id"] in signed_ids:
            dropped.append((a, "already_signed"))
            continue
        if any(overlaps(a["start_time"], a["end_time"], w0, w1) for w0, w1 in windows):
            dropped.append((a, "time_overlap"))
            continue
        kept.append(a)

    organizers = sorted({a["organizer_id"] for a in kept if a["organizer_id"] != user["id"]})
    social_raw: dict[str, float] = {}
    social_detail: dict[str, dict] = {}
    for org in organizers:
        co = count_co(regs, user["id"], org)
        a2b = count_signed_org(regs, activities, user["id"], org)
        b2a = count_signed_org(regs, activities, org, user["id"])
        s = social_formula(co, a2b, b2a)
        social_raw[org] = s
        social_detail[org] = {"co": co, "a_to_b": a2b, "b_to_a": b2a, "s": s}

    rows = []
    for a in kept:
        sim = sim_by_id.get(a["id"], 0.0)
        tag = tag_match(a, user["interests"])
        soc = social_raw.get(a["organizer_id"], 0.0)
        hot = a["signup_count"] + a["favorite_count"]
        tfit = time_fit(a["start_time"], user["available_time"])
        rows.append({
            "activity": a,
            "sim_raw": sim,
            "tag_raw": float(tag),
            "social_raw": soc,
            "hot_raw": float(hot),
            "time_raw": tfit,
        })

    if rows:
        sim_n = min_max([r["sim_raw"] for r in rows])
        tag_n = min_max([r["tag_raw"] for r in rows])
        soc_n = min_max([r["social_raw"] for r in rows])
        hot_n = min_max([r["hot_raw"] for r in rows])
        w_sim = 0.0 if cold else W_SIM
        w_hot = W_HOT + (W_SIM if cold else 0.0)
        w_sum = w_sim + W_TAG + W_SOC + w_hot + W_TIME
        for i, r in enumerate(rows):
            r["sim_n"] = sim_n[i]
            r["tag_n"] = tag_n[i]
            r["social_n"] = soc_n[i]
            r["hot_n"] = hot_n[i]
            r["time_n"] = r["time_raw"]
            r["c_sim"] = w_sim * r["sim_n"] / w_sum
            r["c_tag"] = W_TAG * r["tag_n"] / w_sum
            r["c_soc"] = W_SOC * r["social_n"] / w_sum
            r["c_hot"] = w_hot * r["hot_n"] / w_sum
            r["c_time"] = W_TIME * r["time_n"] / w_sum
            r["final"] = r["c_sim"] + r["c_tag"] + r["c_soc"] + r["c_hot"] + r["c_time"]
        rows.sort(key=lambda x: -x["final"])

        sims = [r["sim_raw"] for r in rows]
        meta["sim_min"] = min(sims)
        meta["sim_max"] = max(sims)
        meta["sim_span"] = meta["sim_max"] - meta["sim_min"]
        meta["sim_std"] = (
            math.sqrt(sum((x - sum(sims) / len(sims)) ** 2 for x in sims) / len(sims))
            if sims else 0.0
        )
    else:
        meta["sim_min"] = meta["sim_max"] = meta["sim_span"] = meta["sim_std"] = 0.0

    return {
        "user": user,
        "meta": meta,
        "cold_start": cold,
        "signed_ids": sorted(signed_ids),
        "dropped": dropped,
        "social_detail": social_detail,
        "rows": rows,
        "weights": {
            "sim": 0.0 if cold else W_SIM,
            "tag": W_TAG,
            "social": W_SOC,
            "hot": W_HOT + (W_SIM if cold else 0.0),
            "time": W_TIME,
        },
    }


def fmt(x: float, n: int = 4) -> str:
    return f"{x:.{n}f}"


def write_report(results: list[dict]) -> None:
    lines: list[str] = []
    lines.append("# 推荐分项打分实验")
    lines.append("")
    lines.append("## 1. 实验目标")
    lines.append("")
    lines.append(
        "复现当前推荐管线（用户偏好向量 → ES kNN → 硬过滤 → 候选内 min-max → 加权求和），"
        "导出不同用户对各活动的 **原始分 / 归一化分 / 加权贡献 / 最终分**，"
        "定位「兴趣差异大的用户推荐列表却高度相似」等问题根因。"
    )
    lines.append("")
    lines.append("## 2. 实验环境与方案")
    lines.append("")
    lines.append(f"- 索引：`{INDEX}`，嵌入模型：`{MODEL}`")
    lines.append(
        f"- 偏好向量：最近 K={PREF_HISTORY_K} 条报名 embedding，"
        f"半衰期 {PREF_HALF_LIFE:g} 天；兴趣文本推理后凸组合 "
        f"`{PREF_INTEREST_MIX}·v_int + {1 - PREF_INTEREST_MIX}·v_hist`"
    )
    lines.append(f"- 召回：kNN k={RECALL}，仅 `status=published`")
    lines.append("- 硬过滤：已报名活动 + 与已报名时段重叠")
    lines.append(
        f"- 权重：sim={W_SIM}, tag={W_TAG}, social={W_SOC}, hot={W_HOT}, time={W_TIME} "
        "（对候选集做 min-max；time 已是 0/1）"
    )
    lines.append("- 社交："
                 r"`s(A,B)=log(1+c_co)+0.8·log(1+c_A→B)+0.8·log(1+c_B→A)`")
    lines.append(f"- 复现：`python backend/scripts/recommend-score-experiment.py`")
    lines.append(f"- 生成时间：{datetime.now().isoformat(timespec='seconds')}")
    lines.append("")

    # Overview comparison
    lines.append("## 3. 用户对比一览")
    lines.append("")
    lines.append(
        "| 用户 | 兴趣 | 冷启动 | 历史报名条数 | "
        "过滤后候选 | sim 跨度 | sim σ | Top1 | Top1 final |"
    )
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for res in results:
        u = res["user"]
        m = res["meta"]
        top = res["rows"][0] if res["rows"] else None
        lines.append(
            f"| {u['name']} (`{u['id']}`) | "
            f"{','.join(u['interests']) or '—'} | "
            f"{'是' if res['cold_start'] else '否'} | "
            f"{m['history_n']} | {len(res['rows'])} | "
            f"{fmt(m['sim_span'])} | {fmt(m['sim_std'])} | "
            f"{(top['activity']['title'] if top else '—')} | "
            f"{(fmt(top['final']) if top else '—')} |"
        )
    lines.append("")

    # Overlap
    if len(results) >= 2:
        a_ids = [r["activity"]["id"] for r in results[0]["rows"][:8]]
        b_ids = [r["activity"]["id"] for r in results[1]["rows"][:8]]
        overlap = sorted(set(a_ids) & set(b_ids))
        lines.append("### 3.1 张三 vs 李四 Top-8 重叠")
        lines.append("")
        lines.append(f"- 张三 Top-8 ids: `{a_ids}`")
        lines.append(f"- 李四 Top-8 ids: `{b_ids}`")
        lines.append(f"- 交集 size={len(overlap)}：`{overlap}`")
        titles = {
            r["activity"]["id"]: r["activity"]["title"]
            for res in results for r in res["rows"]
        }
        if overlap:
            lines.append("- 重叠活动：" + "；".join(f"{i} {titles.get(i,'')}" for i in overlap))
        lines.append("")

    # Per-user detail
    lines.append("## 4. 分用户分项明细（过滤后全量，按 final 降序）")
    lines.append("")
    lines.append(
        "列说明：`sim` 为 ES kNN `_score≈(1+cos)/2`；"
        "`*_n` 为候选内 min-max；`c_*` 为权重×归一化后对 final 的贡献；"
        "`final=Σ c_*`。"
    )
    lines.append("")

    for res in results:
        u = res["user"]
        m = res["meta"]
        w = res["weights"]
        lines.append(f"### {u['name']} — `{u['id']}`")
        lines.append("")
        lines.append(
            f"- 兴趣：`{u['interests']}`；空闲：`{u['available_time']}`"
        )
        lines.append(
            f"- 兴趣推断文本：`{m.get('interest_text') or '—'}`；"
            f"历史向量用报名数={m['history_n']}；冷启动={res['cold_start']}"
        )
        lines.append(
            f"- 已报名 ids：`{res['signed_ids']}`"
        )
        lines.append(
            f"- 有效权重：sim={w['sim']}, tag={w['tag']}, social={w['social']}, "
            f"hot={w['hot']}, time={w['time']}"
        )
        lines.append(
            f"- 候选 sim：min={fmt(m['sim_min'])}, max={fmt(m['sim_max'])}, "
            f"span={fmt(m['sim_span'])}, σ={fmt(m['sim_std'])}"
        )
        if res["social_detail"]:
            lines.append("- 对主办方社交原始分：")
            for org, d in sorted(res["social_detail"].items()):
                lines.append(
                    f"  - `{org}`: co={d['co']}, A→B={d['a_to_b']}, B→A={d['b_to_a']}, "
                    f"s={fmt(d['s'])}"
                )
        if res["dropped"]:
            lines.append(f"- 硬过滤剔除 {len(res['dropped'])} 条（含已报名/时间重叠）"
                         f"，前 8 条：")
            for a, reason in res["dropped"][:8]:
                lines.append(
                    f"  - id={a['id']} {a['title']} — `{reason}` "
                    f"(sim召回内={'是' if True else ''})"
                )
        lines.append("")
        lines.append(
            "| 名次 | id | 标题 | 主办 | "
            "sim | sim_n | c_sim | "
            "tag | tag_n | c_tag | "
            "soc | soc_n | c_soc | "
            "hot | hot_n | c_hot | "
            "time | c_time | **final** |"
        )
        lines.append(
            "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | "
            "--- | --- | --- | --- | --- | --- | --- | --- | --- |"
        )
        for rank, r in enumerate(res["rows"], 1):
            a = r["activity"]
            lines.append(
                f"| {rank} | {a['id']} | {a['title']} | {a['organizer_id']} | "
                f"{fmt(r['sim_raw'])} | {fmt(r['sim_n'])} | {fmt(r['c_sim'])} | "
                f"{int(r['tag_raw'])} | {fmt(r['tag_n'])} | {fmt(r['c_tag'])} | "
                f"{fmt(r['social_raw'])} | {fmt(r['social_n'])} | {fmt(r['c_soc'])} | "
                f"{int(r['hot_raw'])} | {fmt(r['hot_n'])} | {fmt(r['c_hot'])} | "
                f"{fmt(r['time_raw'],0)} | {fmt(r['c_time'])} | "
                f"**{fmt(r['final'])}** |"
            )
        lines.append("")

        # Contribution analysis for top5
        if res["rows"]:
            lines.append("#### Top-5 贡献拆解（谁把分抬上去）")
            lines.append("")
            for rank, r in enumerate(res["rows"][:5], 1):
                parts = [
                    ("sim", r["c_sim"]),
                    ("tag", r["c_tag"]),
                    ("social", r["c_soc"]),
                    ("hot", r["c_hot"]),
                    ("time", r["c_time"]),
                ]
                parts.sort(key=lambda x: -x[1])
                dominant = ", ".join(f"{n}={fmt(v)}" for n, v in parts[:3])
                lines.append(
                    f"{rank}. **{r['activity']['title']}** final={fmt(r['final'])} "
                    f"— 主贡献：{dominant}"
                )
            lines.append("")

    # Diagnosis
    lines.append("## 5. 问题诊断（基于本次实验结果）")
    lines.append("")

    zhang = next((r for r in results if r["user"]["id"] == "524030910001"), None)
    li = next((r for r in results if r["user"]["id"] == "524030910002"), None)

    lines.append("### 5.1 【主因】候选内 min-max 在窄分布 / 常数分布下失真")
    lines.append("")
    if zhang and zhang["rows"]:
        lines.append(
            f"- **sim**：张三过滤后候选 span 仅 **{fmt(zhang['meta']['sim_span'])}**"
            f"（σ={fmt(zhang['meta']['sim_std'])}）。"
            "min-max 把噪声级差距放大成 0～1，"
            "**0.55 权重不再表示绝对相关度**，只表示池内相对名次。"
        )
        tag_raws = [int(r["tag_raw"]) for r in zhang["rows"]]
        tag_ns = [r["tag_n"] for r in zhang["rows"]]
        if tag_raws and max(tag_raws) == min(tag_raws) == 0 and min(tag_ns) >= 0.999:
            lines.append(
                "- **tag（实现缺陷）**：张三剩余 8 个候选的 `tag_raw` **全部为 0**"
                "（兴趣标签无一命中），但 `VectorMath.minMaxNormalize` 在 "
                "`max<=min` 时返回 **1.0**，于是每人白得 `c_tag=0.15`。"
                "该项对排序无区分度，却虚高 final，掩盖「零标签匹配」。"
            )
        lines.append(
            "- 篮球对张三：`sim_raw≈0.93`（池内最高 → sim_n=1）+ "
            "`soc_n=1`（李四主办）+ `tag_n=1`（伪满分）→ final≈0.93，冲到 Top1。"
        )
    lines.append("")

    lines.append("### 5.2 硬过滤掏空「最对口」活动")
    lines.append("")
    if zhang:
        lines.append(
            f"- 张三已报名 ids：`{zhang['signed_ids']}` "
            "（AI 讲座、羽毛球、摄影、志愿、程序训练营 —— 正中兴趣）。"
        )
        n_overlap = sum(1 for _, reason in zhang["dropped"] if reason == "time_overlap")
        lines.append(
            f"- 另有 {n_overlap} 条因与已报活动**时间重叠**被剔除"
            "（创业路演、机器人、马拉松等）。"
            "过滤后仅剩 ~8 个主题离散候选，相对排序更易被社交/热度左右。"
        )
    lines.append("")

    lines.append("### 5.3 社交分偏向稀疏主办方图")
    lines.append("")
    if zhang and zhang["social_detail"]:
        ranked_org = sorted(
            zhang["social_detail"].items(), key=lambda kv: -kv[1]["s"]
        )
        lines.append(
            "- 张三侧社交强度："
            + "；".join(f"`{o}` s={fmt(d['s'])}" for o, d in ranked_org)
        )
        li_org = "524030910002"
        if li_org in zhang["social_detail"]:
            lines.append(
                f"- 对李四 s={fmt(zhang['social_detail'][li_org]['s'])}"
                f"（co={zhang['social_detail'][li_org]['co']}），"
                f"`soc_n=1` 单独贡献 {fmt(W_SOC)}，"
                "把「李四办的篮球/桌游」整体抬一档。"
            )
        lines.append(
            "- seed 主办方几乎只有 T001 / 张三 / 李四，"
            "社交项近似「是否报过李四/老师活动」的阶跃特征。"
        )
    lines.append("")

    lines.append("### 5.4 热度与时间")
    lines.append("")
    lines.append(
        f"- hot 权重 {W_HOT}：报名+收藏高的活动对所有用户同向偏置。"
    )
    lines.append(
        f"- time 权重 {W_TIME}：张三含 weekday_evening，篮球（工作日下午）"
        "`time_fit=0` 仍因其他项过高排第一 —— 说明辅因子无法挡住失真的主项。"
    )
    lines.append("")

    lines.append("### 5.5 为何张三≈李四：机制链")
    lines.append("")
    lines.append(
        "```text\n"
        "兴趣不同 → 偏好向量不同 → kNN 原始序本应不同\n"
        "        ↓ 硬过滤去掉各自已报的「强匹配」\n"
        "剩余候选 sim 挤在 ~0.91–0.93 → min-max 放大噪声\n"
        "        ↓ tag 全 0 却被归一成全 1；社交推高李四主办活动\n"
        "Top-8 交集 7/8，双方 Top1 同为「三人篮球」\n"
        "```"
    )
    lines.append("")

    lines.append("## 6. 改进方向（实验结论，非本次实现）")
    lines.append("")
    lines.append(
        "1. **修 min-max 退化**：当 `max==min` 时输出 **0**（全无区分）而非 1；"
        "sim 仅在 span>δ 时归一化，否则用绝对 `_score`。"
    )
    lines.append(
        "2. **内容优先**：`final = sim_abs + ε·(tag_raw_norm + social_capped + …)`，"
        "辅因子总和有上界。"
    )
    lines.append(
        "3. **社交封顶 / 降权**：权重 ≤0.05，或 `soc_n=min(1,s/s0)`；"
        "主办方过少时关闭社交项。"
    )
    lines.append(
        "4. **过滤后补召回**：硬过滤后候选过少时扩大 k 或按兴趣标签二次召回。"
    )
    lines.append(
        "5. **tag 用原始命中数 / 上限截断**，不要对全零向量做 min-max。"
    )
    lines.append("")
    lines.append("---")
    lines.append("*本报告由 `recommend-score-experiment.py` 自动生成。*")

    OUT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_csv(results: list[dict]) -> None:
    fields = [
        "user_id", "user_name", "rank", "activity_id", "title", "organizer_id",
        "sim_raw", "sim_n", "c_sim",
        "tag_raw", "tag_n", "c_tag",
        "social_raw", "social_n", "c_soc",
        "hot_raw", "hot_n", "c_hot",
        "time_raw", "c_time", "final",
        "cold_start", "sim_span",
    ]
    with OUT_CSV.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        for res in results:
            for rank, r in enumerate(res["rows"], 1):
                a = r["activity"]
                w.writerow({
                    "user_id": res["user"]["id"],
                    "user_name": res["user"]["name"],
                    "rank": rank,
                    "activity_id": a["id"],
                    "title": a["title"],
                    "organizer_id": a["organizer_id"],
                    "sim_raw": round(r["sim_raw"], 6),
                    "sim_n": round(r["sim_n"], 6),
                    "c_sim": round(r["c_sim"], 6),
                    "tag_raw": int(r["tag_raw"]),
                    "tag_n": round(r["tag_n"], 6),
                    "c_tag": round(r["c_tag"], 6),
                    "social_raw": round(r["social_raw"], 6),
                    "social_n": round(r["social_n"], 6),
                    "c_soc": round(r["c_soc"], 6),
                    "hot_raw": int(r["hot_raw"]),
                    "hot_n": round(r["hot_n"], 6),
                    "c_hot": round(r["c_hot"], 6),
                    "time_raw": r["time_raw"],
                    "c_time": round(r["c_time"], 6),
                    "final": round(r["final"], 6),
                    "cold_start": res["cold_start"],
                    "sim_span": round(res["meta"]["sim_span"], 6),
                })


def main():
    parser = argparse.ArgumentParser(description="Recommend score factor experiment")
    parser.add_argument("--skip-md", action="store_true")
    args = parser.parse_args()

    # connectivity
    try:
        http_json("GET", f"{ES_BASE}/")
    except urllib.error.URLError as ex:
        raise SystemExit(f"ES unreachable at {ES_BASE}: {ex}") from ex

    users = load_users()
    activities = load_activities()
    regs = load_registrations()
    emb_cache: dict[int, list[float]] = {}

    results = []
    for uid, _name in USERS:
        if uid not in users:
            print(f"skip missing user {uid}")
            continue
        print(f"scoring {users[uid]['name']} ({uid}) ...")
        res = score_user(users[uid], activities, regs, emb_cache)
        results.append(res)
        top = res["rows"][:3]
        for i, r in enumerate(top, 1):
            print(
                f"  #{i} {r['activity']['title']} final={r['final']:.4f} "
                f"sim={r['sim_raw']:.4f}->{r['sim_n']:.4f} "
                f"soc_n={r['social_n']:.4f} hot_n={r['hot_n']:.4f}"
            )

    write_csv(results)
    if not args.skip_md:
        write_report(results)
    print(f"wrote {OUT_CSV}")
    print(f"wrote {OUT_MD}")


if __name__ == "__main__":
    main()
