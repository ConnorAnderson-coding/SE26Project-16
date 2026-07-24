#!/usr/bin/env python3
"""
Metric MDS 二维可视化：活动 embedding、搜索词向量、用户喜好向量。

用余弦距离 d_ij = 1 - cos(v_i, v_j) 做 Metric MDS，使图中点距近似语义距离。
每张图对其中全部点联合建距并一次 MDS，保证同图内可比。

Outputs:
  - report/embedding-mds-figures/*.png
  - report/embedding-mds-visualize.md

Dependencies: pip install numpy matplotlib scikit-learn

Usage:
  python backend/scripts/embedding-mds-visualize.py
  python backend/scripts/embedding-mds-visualize.py --user 524030910001
"""

from __future__ import annotations

import argparse
import json
import subprocess
import urllib.request
from datetime import date, datetime
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib import font_manager
from matplotlib.patches import FancyArrowPatch
from sklearn.manifold import MDS

ES_BASE = "http://127.0.0.1:9200"
INDEX = "campus_activities"
MODEL = "campus_gte"

MYSQL = [
    "docker", "exec", "campus-mysql",
    "mysql", "-ucampus", "-pcampus123",
    "--default-character-set=utf8mb4",
    "campus_activity", "-N", "-B",
]

PREF_HISTORY_K = 10
PREF_HALF_LIFE = 30.0
PREF_INTEREST_MIX = 0.4

USERS = [
    ("524030910001", "张三"),
    ("524030910002", "李四"),
    ("T001", "王老师"),
]

QUERIES = [
    ("exact_羽毛球", "羽毛球", "exact"),
    ("exact_量子物理", "量子物理", "exact"),
    ("synonym_球类运动", "球类运动友谊赛", "synonym"),
    ("synonym_手工甜点", "手工甜点烘焙", "synonym"),
    ("theme_周末放松", "周末放松解压", "theme"),
    ("theme_找伙伴玩", "周末找朋友一起玩", "theme"),
    ("theme_休闲", "休闲", "theme"),
    ("unrelated_量子纠缠", "量子纠缠实验报告", "unrelated"),
    ("unrelated_有机合成", "有机合成试剂配制", "unrelated"),
]

CATEGORY_ZH = {
    "academic": "学术",
    "sports": "体育",
    "arts": "文艺",
    "club": "社团",
    "innovation": "科创",
    "volunteer": "志愿",
}

CATEGORY_COLORS = {
    "academic": "#4C78A8",
    "sports": "#F58518",
    "arts": "#E45756",
    "club": "#72B7B2",
    "innovation": "#54A24B",
    "volunteer": "#B279A2",
}

QUERY_TYPE_COLORS = {
    "exact": "#D62728",
    "synonym": "#FF7F0E",
    "theme": "#2CA02C",
    "unrelated": "#7F7F7F",
}

PROJECT_ROOT = Path(__file__).resolve().parents[2]
FIG_DIR = PROJECT_ROOT / "report" / "embedding-mds-figures"
OUT_MD = PROJECT_ROOT / "report" / "embedding-mds-visualize.md"


# ---------------------------------------------------------------------------
# Font / IO
# ---------------------------------------------------------------------------

def setup_chinese_font() -> str | None:
    candidates = [
        "Microsoft YaHei",
        "SimHei",
        "PingFang SC",
        "Noto Sans CJK SC",
        "WenQuanYi Micro Hei",
        "Arial Unicode MS",
    ]
    available = {f.name for f in font_manager.fontManager.ttflist}
    for name in candidates:
        if name in available:
            plt.rcParams["font.sans-serif"] = [name] + plt.rcParams.get("font.sans-serif", [])
            plt.rcParams["axes.unicode_minus"] = False
            return name
    plt.rcParams["axes.unicode_minus"] = False
    return None


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


# ---------------------------------------------------------------------------
# Vector math
# ---------------------------------------------------------------------------

def l2_normalize(vec: list[float] | np.ndarray) -> list[float]:
    arr = np.asarray(vec, dtype=np.float64)
    n = float(np.linalg.norm(arr))
    if n <= 1e-12:
        return arr.tolist()
    return (arr / n).tolist()


def weighted_average(vectors: list[list[float]], weights: list[float]) -> list[float] | None:
    if not vectors:
        return None
    dim = len(vectors[0])
    acc = np.zeros(dim, dtype=np.float64)
    wsum = 0.0
    for v, w in zip(vectors, weights):
        if w <= 0 or len(v) != dim:
            continue
        acc += w * np.asarray(v, dtype=np.float64)
        wsum += w
    if wsum <= 0:
        return None
    return l2_normalize((acc / wsum).tolist())


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


def short_title(title: str, max_len: int = 8) -> str:
    t = title.replace("—", "-").replace("–", "-")
    if len(t) <= max_len:
        return t
    return t[: max_len - 1] + "…"


# ---------------------------------------------------------------------------
# MDS
# ---------------------------------------------------------------------------

def cosine_distance_matrix(vectors: np.ndarray) -> np.ndarray:
    """d_ij = 1 - cos(vi, vj); assumes rows are already L2-normalized."""
    X = np.asarray(vectors, dtype=np.float64)
    norms = np.linalg.norm(X, axis=1, keepdims=True)
    norms = np.maximum(norms, 1e-12)
    Xn = X / norms
    sim = np.clip(Xn @ Xn.T, -1.0, 1.0)
    D = 1.0 - sim
    np.fill_diagonal(D, 0.0)
    return D


def fit_mds(vectors: list[list[float]] | np.ndarray, random_state: int = 42) -> tuple[np.ndarray, float]:
    X = np.asarray(vectors, dtype=np.float64)
    D = cosine_distance_matrix(X)
    mds = MDS(
        n_components=2,
        metric=True,
        dissimilarity="precomputed",
        random_state=random_state,
        n_init=4,
        max_iter=300,
        normalized_stress=False,
    )
    xy = mds.fit_transform(D)
    raw = float(mds.stress_)
    # Kruskal Stress-1 style: sqrt(raw / sum d^2) for readable [0,1]-ish scale
    denom = float(np.sum(D ** 2))
    stress1 = float(np.sqrt(raw / denom)) if denom > 1e-18 else raw
    return xy, stress1


# ---------------------------------------------------------------------------
# ES / MySQL
# ---------------------------------------------------------------------------

def infer_embedding(text: str) -> list[float] | None:
    body = {"docs": [{"text_field": text}]}
    root = http_json("POST", f"{ES_BASE}/_ml/trained_models/{MODEL}/_infer", body)
    emb = None
    try:
        emb = root["inference_results"][0]["predicted_value"]
    except (KeyError, IndexError, TypeError):
        pass
    if not emb:
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


def load_activities_with_embeddings() -> list[dict]:
    root = http_json("POST", f"{ES_BASE}/{INDEX}/_search", {
        "size": 500,
        "query": {"match_all": {}},
        "_source": ["id", "title", "category", "tags", "status", "activity_embedding"],
        "sort": [{"id": "asc"}],
    })
    hits = root.get("hits", {}).get("hits", [])
    rows = []
    for h in hits:
        src = h.get("_source") or {}
        emb = src.get("activity_embedding")
        if not emb:
            continue
        aid = src.get("id")
        if aid is None:
            aid = int(h["_id"])
        else:
            aid = int(aid)
        rows.append({
            "id": aid,
            "title": src.get("title") or "",
            "category": src.get("category") or "unknown",
            "tags": src.get("tags") or [],
            "status": src.get("status") or "",
            "embedding": l2_normalize([float(x) for x in emb]),
        })
    if not rows:
        raise RuntimeError(
            "ES 中无活动 embedding。请先启动索引重建：POST /api/v1/search/index/rebuild"
        )
    return rows


def load_users() -> dict[str, dict]:
    ids = ",".join(f"'{u}'" for u, _ in USERS)
    rows = mysql_rows(
        f"SELECT id, name, interests, available_time FROM user WHERE id IN ({ids})"
    )
    users = {}
    for r in rows:
        users[r[0]] = {
            "id": r[0],
            "name": r[1],
            "interests": parse_json_list(r[2] if len(r) > 2 else None),
            "available_time": parse_json_list(r[3] if len(r) > 3 else None),
        }
    return users


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


def build_pref_components(
    user: dict,
    regs: list[dict],
    emb_cache: dict[int, list[float]],
    activities_by_id: dict[int, dict],
) -> dict:
    result: dict = {
        "interest_vec": None,
        "history_vec": None,
        "pref_vec": None,
        "interest_text": "",
        "history_items": [],
        "cold_start": False,
    }
    interests = user["interests"]
    interest_vec = None
    if interests:
        text = " ".join(interests)
        result["interest_text"] = text
        raw = infer_embedding(text)
        if raw:
            interest_vec = l2_normalize(raw)
            result["interest_vec"] = interest_vec

    uid = user["id"]
    uregs = [
        r for r in regs
        if r["user_id"] == uid and (r["status"] is None or r["status"] != "rejected")
    ]
    uregs.sort(key=lambda r: r["created_at"] or datetime.min, reverse=True)
    uregs = uregs[:PREF_HISTORY_K]

    vectors, weights = [], []
    today = date.today()
    need_ids = [r["activity_id"] for r in uregs if r["activity_id"] not in emb_cache]
    if need_ids:
        emb_cache.update(mget_embeddings(need_ids))

    for r in uregs:
        aid = r["activity_id"]
        emb = emb_cache.get(aid)
        if not emb:
            continue
        day = (r["created_at"].date() if r["created_at"] else today)
        days_ago = (today - day).days
        w = time_decay(days_ago, PREF_HALF_LIFE)
        vectors.append(emb)
        weights.append(w)
        act = activities_by_id.get(aid) or {}
        result["history_items"].append({
            "activity_id": aid,
            "title": act.get("title", f"#{aid}"),
            "category": act.get("category", ""),
            "days_ago": days_ago,
            "weight": w,
            "embedding": emb,
        })

    history_vec = weighted_average(vectors, weights)
    result["history_vec"] = history_vec

    if interest_vec is None and history_vec is None:
        result["cold_start"] = True
        return result
    if interest_vec is None:
        result["pref_vec"] = history_vec
    elif history_vec is None:
        result["pref_vec"] = interest_vec
    else:
        result["pref_vec"] = convex_combine(interest_vec, history_vec, PREF_INTEREST_MIX)
    return result


# ---------------------------------------------------------------------------
# Plots
# ---------------------------------------------------------------------------

def plot_activities(activities: list[dict], xy: np.ndarray, stress: float, path: Path) -> None:
    fig, ax = plt.subplots(figsize=(10, 7.5), dpi=160)
    cats = sorted({a["category"] for a in activities})
    for cat in cats:
        mask = [i for i, a in enumerate(activities) if a["category"] == cat]
        pts = xy[mask]
        ax.scatter(
            pts[:, 0], pts[:, 1],
            c=CATEGORY_COLORS.get(cat, "#888"), s=70, alpha=0.85,
            edgecolors="white", linewidths=0.6,
            label=CATEGORY_ZH.get(cat, cat), zorder=3,
        )
        for i in mask:
            ax.annotate(
                f'{activities[i]["id"]}.{short_title(activities[i]["title"], 6)}',
                (xy[i, 0], xy[i, 1]),
                textcoords="offset points", xytext=(5, 4),
                fontsize=7, color="#333333",
            )
    ax.set_xlabel("MDS-1（余弦距离嵌入）")
    ax.set_ylabel("MDS-2（余弦距离嵌入）")
    ax.set_title(f"图1 · 全部活动的 Metric MDS 分布（stress={stress:.4f}）")
    ax.legend(title="活动类别", loc="best", framealpha=0.92)
    ax.axhline(0, color="#cccccc", lw=0.6)
    ax.axvline(0, color="#cccccc", lw=0.6)
    ax.grid(True, alpha=0.25)
    ax.margins(0.12)
    fig.tight_layout()
    fig.savefig(path, bbox_inches="tight")
    plt.close(fig)


def plot_activities_queries(
    activities: list[dict],
    act_xy: np.ndarray,
    queries: list[dict],
    stress: float,
    path: Path,
) -> None:
    fig, ax = plt.subplots(figsize=(10.5, 8), dpi=160)
    ax.scatter(
        act_xy[:, 0], act_xy[:, 1],
        c="#B0B0B0", s=55, alpha=0.55, edgecolors="white", linewidths=0.5,
        label="活动", zorder=2, marker="o",
    )
    for i, a in enumerate(activities):
        ax.annotate(
            str(a["id"]),
            (act_xy[i, 0], act_xy[i, 1]),
            textcoords="offset points", xytext=(3, 3),
            fontsize=6, color="#666666",
        )
    for qtype, qlabel in [
        ("exact", "搜索·完全匹配"),
        ("synonym", "搜索·近义"),
        ("theme", "搜索·主题"),
        ("unrelated", "搜索·几乎无关"),
    ]:
        pts = [q for q in queries if q["qtype"] == qtype]
        if not pts:
            continue
        xs = [p["xy"][0] for p in pts]
        ys = [p["xy"][1] for p in pts]
        ax.scatter(
            xs, ys,
            c=QUERY_TYPE_COLORS[qtype], s=140, marker="D",
            edgecolors="black", linewidths=0.7, alpha=0.95,
            label=qlabel, zorder=4,
        )
        for p in pts:
            ax.annotate(
                p["query"],
                p["xy"],
                textcoords="offset points", xytext=(6, 5),
                fontsize=8, fontweight="bold", color=QUERY_TYPE_COLORS[qtype],
            )
    ax.set_xlabel("MDS-1（余弦距离嵌入）")
    ax.set_ylabel("MDS-2（余弦距离嵌入）")
    ax.set_title(f"图2 · 活动与搜索词联合 MDS（stress={stress:.4f}）")
    ax.legend(loc="best", framealpha=0.92)
    ax.axhline(0, color="#cccccc", lw=0.6)
    ax.axvline(0, color="#cccccc", lw=0.6)
    ax.grid(True, alpha=0.25)
    ax.margins(0.15)
    fig.tight_layout()
    fig.savefig(path, bbox_inches="tight")
    plt.close(fig)


def plot_user_composition(
    activities: list[dict],
    act_xy: np.ndarray,
    hist_xy_by_aid: dict[int, tuple[float, float]],
    user: dict,
    components: dict,
    special_xy: dict[str, tuple[float, float]],
    stress: float,
    path: Path,
) -> None:
    fig, ax = plt.subplots(figsize=(11, 8.2), dpi=160)
    cats = sorted({a["category"] for a in activities})
    for cat in cats:
        mask = [i for i, a in enumerate(activities) if a["category"] == cat]
        pts = act_xy[mask]
        ax.scatter(
            pts[:, 0], pts[:, 1],
            c=CATEGORY_COLORS.get(cat, "#888"), s=40, alpha=0.25,
            edgecolors="none", zorder=1,
        )

    for item in components["history_items"]:
        xy = hist_xy_by_aid.get(item["activity_id"])
        if xy is None:
            continue
        ax.scatter(
            [xy[0]], [xy[1]],
            c="#555555", s=90, marker="o", edgecolors="white", linewidths=0.8, zorder=3,
        )
        ax.annotate(
            f'历史 w={item["weight"]:.2f}\n{short_title(item["title"], 10)}',
            xy, textcoords="offset points", xytext=(6, -10),
            fontsize=7, color="#444444",
        )

    def mark(key: str, marker: str, color: str, label: str, size: float = 220):
        xy = special_xy.get(key)
        if xy is None:
            return None
        ax.scatter(
            [xy[0]], [xy[1]],
            c=color, s=size, marker=marker, edgecolors="black", linewidths=1.0,
            label=label, zorder=5,
        )
        return xy

    xy_int = mark("interest", "^", "#1F77B4", r"$v_{int}$ 兴趣向量", 220)
    xy_hist = mark("history", "s", "#FF7F0E", r"$v_{hist}$ 历史向量", 200)
    xy_pref = mark("pref", "*", "#D62728", r"$v_u$ 最终喜好", 380)

    if xy_pref is not None:
        for src, color in [(xy_int, "#1F77B4"), (xy_hist, "#FF7F0E")]:
            if src is None:
                continue
            ax.add_patch(FancyArrowPatch(
                src, xy_pref,
                arrowstyle="-|>", mutation_scale=14,
                color=color, lw=1.4, alpha=0.85, zorder=4,
                connectionstyle="arc3,rad=0.08",
            ))

    note_lines = [
        f"用户：{user['name']}（{user['id']}）",
        f"兴趣：{components['interest_text'] or '（无）'}",
        f"历史报名条数：{len(components['history_items'])}",
        r"$v_u=\mathrm{normalize}(0.4\,v_{int}+0.6\,v_{hist})$",
        r"$w_i=0.5^{d_i/30}$（半衰期 30 天）",
        f"MDS stress={stress:.4f}",
    ]
    ax.text(
        0.02, 0.98, "\n".join(note_lines),
        transform=ax.transAxes, va="top", ha="left", fontsize=8.5,
        bbox=dict(boxstyle="round,pad=0.45", facecolor="white", edgecolor="#cccccc", alpha=0.92),
        zorder=6,
    )
    ax.set_xlabel("MDS-1（余弦距离嵌入）")
    ax.set_ylabel("MDS-2（余弦距离嵌入）")
    ax.set_title(f"图3 · 用户喜好向量合成示意 — {user['name']}")
    ax.legend(loc="lower right", framealpha=0.92)
    ax.axhline(0, color="#cccccc", lw=0.6)
    ax.axvline(0, color="#cccccc", lw=0.6)
    ax.grid(True, alpha=0.25)
    ax.margins(0.12)
    fig.tight_layout()
    fig.savefig(path, bbox_inches="tight")
    plt.close(fig)


def plot_activities_users(
    activities: list[dict],
    act_xy: np.ndarray,
    user_prefs: list[dict],
    stress: float,
    path: Path,
) -> None:
    fig, ax = plt.subplots(figsize=(10.5, 8), dpi=160)
    cats = sorted({a["category"] for a in activities})
    for cat in cats:
        mask = [i for i, a in enumerate(activities) if a["category"] == cat]
        pts = act_xy[mask]
        ax.scatter(
            pts[:, 0], pts[:, 1],
            c=CATEGORY_COLORS.get(cat, "#888"), s=55, alpha=0.55,
            edgecolors="white", linewidths=0.5,
            label=f"活动·{CATEGORY_ZH.get(cat, cat)}", zorder=2,
        )
    user_markers = ["*", "P", "X"]
    user_colors = ["#D62728", "#9467BD", "#17BECF"]
    for i, up in enumerate(user_prefs):
        if up["xy"] is None:
            continue
        ax.scatter(
            [up["xy"][0]], [up["xy"][1]],
            c=user_colors[i % len(user_colors)],
            s=320, marker=user_markers[i % len(user_markers)],
            edgecolors="black", linewidths=1.0,
            label=f"用户喜好·{up['name']}", zorder=5,
        )
        ax.annotate(
            up["name"],
            up["xy"],
            textcoords="offset points", xytext=(8, 8),
            fontsize=10, fontweight="bold", color=user_colors[i % len(user_colors)],
        )
    ax.set_xlabel("MDS-1（余弦距离嵌入）")
    ax.set_ylabel("MDS-2（余弦距离嵌入）")
    ax.set_title(f"图4 · 活动与用户喜好联合 MDS（stress={stress:.4f}）")
    ax.legend(loc="best", fontsize=8, framealpha=0.92)
    ax.axhline(0, color="#cccccc", lw=0.6)
    ax.axvline(0, color="#cccccc", lw=0.6)
    ax.grid(True, alpha=0.25)
    ax.margins(0.12)
    fig.tight_layout()
    fig.savefig(path, bbox_inches="tight")
    plt.close(fig)


# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------

def write_report(
    n_activities: int,
    stresses: dict[str, float],
    queries: list[dict],
    user_summaries: list[dict],
    font_name: str | None,
) -> None:
    lines: list[str] = []
    lines.append("# 活动 / 用户 Embedding 的 Metric MDS 二维可视化报告")
    lines.append("")
    lines.append("## 1. 实验目标")
    lines.append("")
    lines.append(
        "将活动稠密向量、搜索词向量与用户喜好向量投影到二维平面，"
        "使**图中欧氏距离尽量逼近高维余弦语义距离**，便于项目展示与答辩讲解。"
        "相对 PCA（前两维仅解释约 26% 方差），本报告采用 **Metric MDS**。"
    )
    lines.append("")
    lines.append("## 2. 实验环境与方案")
    lines.append("")
    lines.append(f"- 索引：`{INDEX}`，有效活动 embedding 数 **{n_activities}**")
    lines.append(f"- 嵌入模型：`{MODEL}`（thenlper/gte-small-zh，512-d，cosine）")
    lines.append(r"- 降维：**Metric MDS**，预计算距离 $d_{ij}=1-\cos(v_i,v_j)$")
    lines.append("- 联合构图：每张图对其中全部点建同一距离矩阵后一次嵌入，保证同图内点距可比")
    lines.append("- 喜好公式：与线上一致，见 [`doc/检索与推荐.md`](../doc/检索与推荐.md) §6.2")
    lines.append(
        f"  - 历史加权 $w_i=0.5^{{d_i/30}}$；"
        f"凸组合 $v_u=\\mathrm{{normalize}}({PREF_INTEREST_MIX}\\,v_{{int}}"
        f"+{1 - PREF_INTEREST_MIX}\\,v_{{hist}})$"
    )
    lines.append("- MDS Stress-1（$\\sqrt{\\sum(d'_{ij}-d_{ij})^2/\\sum d_{ij}^2}$，越小越好）：")
    for k, v in stresses.items():
        lines.append(f"  - {k}: **{v:.4f}**")
    lines.append("- 复现：")
    lines.append("  ```powershell")
    lines.append("  pip install numpy matplotlib scikit-learn")
    lines.append("  python backend/scripts/embedding-mds-visualize.py")
    lines.append("  ```")
    if font_name:
        lines.append(f"- 绘图中文字体：`{font_name}`")
    lines.append("")
    lines.append("## 3. 方法说明")
    lines.append("")
    lines.append("1. L2 归一化全部向量（与线上 cosine / `VectorMath.normalize` 一致）。")
    lines.append(r"2. 距离矩阵 $D_{ij}=1-\cos(v_i,v_j)$（对角线为 0）。")
    lines.append(
        r'3. `sklearn.manifold.MDS(metric=True, dissimilarity="precomputed")` '
        r"优化二维坐标，使 $\|x_i-x_j\|_2 \approx D_{ij}$；报告中的 stress 为 Kruskal Stress-1。"
    )
    lines.append(
        "4. **为何不用 PCA**：GTE 语义散布在高维，前两主成分累计方差约 26%，平面点距不可代表语义远近；"
        "MDS 直接保距，更适合「距离 ≈ 相关性」的展示目标。"
    )
    lines.append("5. 二维仍有压缩；stress 反映保距误差。正式检索仍以 ES kNN 为准。")
    lines.append("")
    lines.append("## 4. 图1 · 全部活动分布")
    lines.append("")
    lines.append("![图1 活动 MDS](embedding-mds-figures/01-activities-mds.png)")
    lines.append("")
    lines.append(
        f"仅对 {n_activities} 个活动联合 MDS（stress={stresses.get('图1', 0):.4f}）。"
        "类别着色。若同类点相对靠近，说明余弦语义空间中存在可观察的簇"
        "（例如体育活动彼此更近）。"
    )
    lines.append("")
    lines.append("## 5. 图2 · 活动 + 搜索词")
    lines.append("")
    lines.append("![图2 活动与搜索词](embedding-mds-figures/02-activities-queries-mds.png)")
    lines.append("")
    lines.append(
        f"活动与阈值实验搜索词联合 MDS（stress={stresses.get('图2', 0):.4f}）。"
        "搜索词与 [`cosine-threshold-experiment.md`](cosine-threshold-experiment.md) 一致："
    )
    lines.append("")
    lines.append("| 标签 | query | 类型 |")
    lines.append("| --- | --- | --- |")
    for q in queries:
        lines.append(f"| {q['label']} | `{q['query']}` | {q['qtype']} |")
    lines.append("")
    lines.append(
        "灰色圆点为活动，菱形为搜索词。期望：「羽毛球」「球类运动友谊赛」靠近体育活动；"
        "「量子物理」靠近学术活动；主题类靠近休闲/社团；无关查询偏离主活动云或落在别的主题邻域。"
        "图中点距可读作近似语义距离。"
    )
    lines.append("")
    lines.append("## 6. 图3 · 用户喜好向量计算示意")
    lines.append("")
    lines.append(
        r"对每个种子用户：活动 ∪ $v_{int}$ ∪ $v_{hist}$ ∪ $v_u$ 联合 MDS。"
        "深色圆点为近期报名（权重标注）；三角/方块/星号分别为兴趣、历史、最终喜好；"
        "箭头表示凸组合方向。"
    )
    lines.append("")
    for us in user_summaries:
        lines.append(f"### {us['name']}（`{us['id']}`）")
        lines.append("")
        lines.append(f"![图3 {us['name']}]({us['fig']})")
        lines.append("")
        lines.append(f"- 兴趣文本：`{us['interest_text'] or '（无）'}`")
        lines.append(f"- 参与合成的历史报名：{us['history_n']} 条")
        lines.append(f"- 本图 MDS stress：{us['stress']:.4f}")
        if us["history_rows"]:
            lines.append("")
            lines.append("| 活动 | 天数 $d$ | 权重 $w$ |")
            lines.append("| --- | ---: | ---: |")
            for row in us["history_rows"]:
                lines.append(
                    f"| {row['title']} (id={row['id']}) | {row['days_ago']} | {row['weight']:.3f} |"
                )
        if us["cold_start"]:
            lines.append("")
            lines.append("> 冷启动：无兴趣且无可用历史向量，无法画最终喜好点。")
        lines.append("")
    lines.append(
        r"合成后的 $v_u$ 在语义距离意义下应落在 $v_{int}$ 与 $v_{hist}$ 之间偏历史一侧（权重 0.6），"
        "与 `UserPreferenceVectorService` 一致。"
    )
    lines.append("")
    lines.append("## 7. 图4 · 活动 + 用户喜好")
    lines.append("")
    lines.append("![图4 活动与用户喜好](embedding-mds-figures/04-activities-users-mds.png)")
    lines.append("")
    lines.append(
        f"活动与三位用户最终 $v_u$ 联合 MDS（stress={stresses.get('图4', 0):.4f}）。"
        "喜好落点邻近的活动类别，应与兴趣标签及近期报名主题大体一致——这正是首页推荐 kNN 所依据的语义邻域。"
    )
    lines.append("")
    lines.append("## 8. 结论与局限")
    lines.append("")
    lines.append("1. **Metric MDS + 余弦距离** 使图上点距可近似解读为语义远近，优于 PCA 展示。")
    lines.append("2. 搜索词相对活动的落点可与阈值实验排序趋势相互印证。")
    lines.append("3. 图3 可直接用于讲解喜好向量流水线（兴趣 ⊕ 时间衰减历史）。")
    lines.append("4. **局限**：二维压缩仍有误差（见 stress）；seed 规模小；正式排序以 ES kNN / 推荐打分为准。")
    lines.append("")

    OUT_MD.write_text("\n".join(lines), encoding="utf-8")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description="Metric MDS visualization of embeddings")
    parser.add_argument(
        "--user",
        action="append",
        dest="focus_users",
        help="User id to include in report fig3 (default: all seed users).",
    )
    args = parser.parse_args()

    font_name = setup_chinese_font()
    FIG_DIR.mkdir(parents=True, exist_ok=True)

    print("Loading activities from Elasticsearch...")
    activities = load_activities_with_embeddings()
    n = len(activities)
    print(f"  {n} activities with embeddings")
    activities_by_id = {a["id"]: a for a in activities}
    emb_cache = {a["id"]: a["embedding"] for a in activities}
    act_vecs = [a["embedding"] for a in activities]

    stresses: dict[str, float] = {}

    # --- Figure 1: activities only ---
    print("MDS figure 1 (activities)...")
    act_xy, s1 = fit_mds(act_vecs)
    stresses["图1"] = s1
    path1 = FIG_DIR / "01-activities-mds.png"
    plot_activities(activities, act_xy, s1, path1)
    print(f"  stress={s1:.4f} -> {path1}")

    # --- Figure 2: activities + queries ---
    print("Inferring query embeddings + MDS figure 2...")
    query_points = []
    q_vecs = []
    for label, query, qtype in QUERIES:
        emb = infer_embedding(query)
        if not emb:
            print(f"  WARN: infer failed for query={query}")
            continue
        emb = l2_normalize(emb)
        query_points.append({"label": label, "query": query, "qtype": qtype, "embedding": emb})
        q_vecs.append(emb)

    joint2 = act_vecs + q_vecs
    xy2, s2 = fit_mds(joint2)
    stresses["图2"] = s2
    act_xy2 = xy2[:n]
    for i, qp in enumerate(query_points):
        qp["xy"] = (float(xy2[n + i, 0]), float(xy2[n + i, 1]))
    path2 = FIG_DIR / "02-activities-queries-mds.png"
    plot_activities_queries(activities, act_xy2, query_points, s2, path2)
    print(f"  stress={s2:.4f} -> {path2}")

    # --- Users ---
    print("Loading users / registrations...")
    users = load_users()
    regs = load_registrations()
    focus_ids = set(args.focus_users) if args.focus_users else {uid for uid, _ in USERS}

    user_components: dict[str, dict] = {}
    for uid, _name in USERS:
        user = users.get(uid)
        if not user:
            print(f"  WARN: user {uid} not in MySQL")
            continue
        user_components[uid] = build_pref_components(user, regs, emb_cache, activities_by_id)

    # --- Figure 3: per user ---
    user_summaries = []
    for uid, uname in USERS:
        comps = user_components.get(uid)
        user = users.get(uid)
        if not comps or not user:
            continue

        special_keys = []
        special_vecs = []
        if comps["interest_vec"] is not None:
            special_keys.append("interest")
            special_vecs.append(comps["interest_vec"])
        if comps["history_vec"] is not None:
            special_keys.append("history")
            special_vecs.append(comps["history_vec"])
        if comps["pref_vec"] is not None:
            special_keys.append("pref")
            special_vecs.append(comps["pref_vec"])

        joint3 = act_vecs + special_vecs
        xy3, s3 = fit_mds(joint3)
        act_xy3 = xy3[:n]
        special_xy = {}
        for i, key in enumerate(special_keys):
            special_xy[key] = (float(xy3[n + i, 0]), float(xy3[n + i, 1]))

        # History points use activity coordinates from the same embedding
        hist_xy = {a["id"]: (float(act_xy3[i, 0]), float(act_xy3[i, 1])) for i, a in enumerate(activities)}

        safe_id = uid.replace("/", "_")
        fig3_name = f"03-user-pref-composition-{safe_id}.png"
        path3 = FIG_DIR / fig3_name
        plot_user_composition(
            activities, act_xy3, hist_xy, user, comps, special_xy, s3, path3,
        )
        print(f"  fig3 {uname} stress={s3:.4f} -> {path3}")
        stresses[f"图3-{uname}"] = s3

        if uid in focus_ids:
            user_summaries.append({
                "id": uid,
                "name": user["name"],
                "fig": f"embedding-mds-figures/{fig3_name}",
                "interest_text": comps["interest_text"],
                "history_n": len(comps["history_items"]),
                "cold_start": comps["cold_start"],
                "stress": s3,
                "history_rows": [
                    {
                        "id": it["activity_id"],
                        "title": it["title"],
                        "days_ago": it["days_ago"],
                        "weight": it["weight"],
                    }
                    for it in comps["history_items"]
                ],
            })

    # --- Figure 4: activities + user prefs ---
    print("MDS figure 4 (activities + user prefs)...")
    pref_list = []
    pref_vecs = []
    for uid, uname in USERS:
        comps = user_components.get(uid)
        if not comps or comps.get("pref_vec") is None:
            continue
        pref_list.append({"id": uid, "name": users[uid]["name"]})
        pref_vecs.append(comps["pref_vec"])

    joint4 = act_vecs + pref_vecs
    xy4, s4 = fit_mds(joint4)
    stresses["图4"] = s4
    act_xy4 = xy4[:n]
    for i, up in enumerate(pref_list):
        up["xy"] = (float(xy4[n + i, 0]), float(xy4[n + i, 1]))
    path4 = FIG_DIR / "04-activities-users-mds.png"
    plot_activities_users(activities, act_xy4, pref_list, s4, path4)
    print(f"  stress={s4:.4f} -> {path4}")

    write_report(n, stresses, query_points, user_summaries, font_name)
    print(f"Wrote {OUT_MD}")
    print("Done.")


if __name__ == "__main__":
    main()
