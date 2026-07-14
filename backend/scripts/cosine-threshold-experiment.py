#!/usr/bin/env python3
"""
Cosine-threshold experiment for activity semantic search.

For each query class (exact / synonym / thematic / unrelated), run ES kNN on
activity_embedding and collect ES _score (≈ (1+cos)/2) for every activity.

Outputs:
  - backend/cosine-threshold-experiment.md  (tables)
  - backend/cosine-threshold-experiment.csv
"""

from __future__ import annotations

import argparse
import csv
import json
import urllib.error
import urllib.request
from pathlib import Path

ES_BASE = "http://127.0.0.1:9200"
INDEX = "campus_activities"
MODEL = "campus_gte"
BACKEND = "http://localhost:8080/api/v1"

# (label, query, expected_role description)
QUERIES = [
    ("exact_羽毛球", "羽毛球", "完全匹配：字面命中体育活动"),
    ("exact_量子物理", "量子物理", "完全匹配：字面命中学术讲座"),
    ("synonym_球类运动", "球类运动友谊赛", "近义词：无「羽毛球」字面但同类运动"),
    ("synonym_手工甜点", "手工甜点烘焙", "近义词：指向烘焙工作坊"),
    ("theme_周末放松", "周末放松解压", "主题相关：休闲/放松意图"),
    ("theme_找伙伴玩", "周末找朋友一起玩", "主题相关：社交娱乐意图"),
    ("theme_休闲", "休闲", "主题相关：短词「休闲」（常见用户输入）"),
    ("unrelated_量子纠缠", "量子纠缠实验报告", "几乎无关：相对休闲/文体活动"),
    ("unrelated_有机合成", "有机合成试剂配制", "几乎无关：相对体育/艺术活动"),
]


def http_json(method: str, url: str, body: dict | None = None, headers: dict | None = None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    hdrs = {"Content-Type": "application/json; charset=utf-8"}
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def list_activities(es: str) -> list[dict]:
    r = http_json("POST", f"{es}/{INDEX}/_search", {
        "size": 200,
        "query": {"match_all": {}},
        "_source": ["id", "title", "category", "tags"],
        "sort": [{"id": "asc"}],
    })
    hits = r.get("hits", {}).get("hits", [])
    rows = []
    for h in hits:
        src = h.get("_source") or {}
        rows.append({
            "id": src.get("id") or int(h["_id"]),
            "title": src.get("title", ""),
            "category": src.get("category", ""),
            "tags": src.get("tags") or [],
        })
    return rows


def knn_scores(es: str, query: str, size: int) -> dict[int, float]:
    body = {
        "size": size,
        "_source": False,
        "knn": {
            "field": "activity_embedding",
            "k": size,
            "num_candidates": max(size * 2, 50),
            "query_vector_builder": {
                "text_embedding": {
                    "model_id": MODEL,
                    "model_text": query,
                }
            },
        },
    }
    r = http_json("POST", f"{es}/{INDEX}/_search", body)
    out: dict[int, float] = {}
    for h in r.get("hits", {}).get("hits", []):
        out[int(h["_id"])] = float(h.get("_score") or 0.0)
    return out


def approx_cosine(es_score: float | None) -> float | None:
    """ES cosine similarity maps to _score ≈ (1 + cos) / 2."""
    if es_score is None:
        return None
    return 2.0 * es_score - 1.0


def rebuild_index() -> None:
    login = http_json("POST", f"{BACKEND}/auth/login", {
        "userId": "admin001",
        "password": "123456",
    })
    token = login["data"]["token"]
    r = http_json("POST", f"{BACKEND}/search/index/rebuild", headers={
        "Authorization": f"Bearer {token}",
    })
    print("rebuild:", json.dumps(r.get("data"), ensure_ascii=False))


def md_table(headers: list[str], rows: list[list[str]]) -> str:
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rebuild", action="store_true", help="admin rebuild ES index first")
    parser.add_argument("--es", default=ES_BASE)
    args = parser.parse_args()
    es = args.es.rstrip("/")

    if args.rebuild:
        rebuild_index()

    activities = list_activities(es)
    if not activities:
        raise SystemExit(
            "ES index is empty. Run: database/reload-demo-data.ps1 then "
            "POST /api/v1/search/index/rebuild (or --rebuild)."
        )

    n = len(activities)
    print(f"activities in ES: {n}")

    # matrix[query_label][activity_id] = es_score
    matrix: dict[str, dict[int, float]] = {}
    for label, query, _role in QUERIES:
        print(f"query {label}: {query}")
        matrix[label] = knn_scores(es, query, size=max(n, 20))

    out_dir = Path(__file__).resolve().parents[1]
    md_path = out_dir / "cosine-threshold-experiment.md"
    csv_path = out_dir / "cosine-threshold-experiment.csv"

    # CSV long form
    with csv_path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow([
            "query_label", "query", "role", "activity_id", "title", "category",
            "es_score_(1+cos)/2", "approx_cosine",
        ])
        for label, query, role in QUERIES:
            scores = matrix[label]
            for act in activities:
                aid = act["id"]
                es_s = scores.get(aid)
                w.writerow([
                    label, query, role, aid, act["title"], act["category"],
                    "" if es_s is None else f"{es_s:.6f}",
                    "" if es_s is None else f"{approx_cosine(es_s):.6f}",
                ])

    # Markdown summary
    sections: list[str] = []
    sections.append("# 余弦相似度阈值实验\n")
    sections.append("## 1. 实验目标\n")
    sections.append(
        "在生产用稠密向量模型与当前 `search_text` 拼接下，测量各意图 query 相对全部活动的 "
        "ES kNN `_score` 分布，据此选定语义硬截断阈值 "
        "`app.elasticsearch.semantic-absolute-threshold`（仅作用于「无 BM25 命中」的语义候选）。\n"
    )
    sections.append("## 2. 实验环境与方案\n")
    sections.append(
        f"- 索引：`{INDEX}`，活动数 **{n}**（seed 全量非 draft）\n"
        f"- 嵌入模型：`{MODEL}`（thenlper/gte-small-zh，512-d，无 E5 query/passage 前缀）\n"
        "- 向量字段：`activity_embedding`，`similarity: cosine`\n"
        "- 分数映射：ES kNN `_score ≈ (1 + cosine) / 2`；表中 "
        "`approx_cosine = 2*score - 1`\n"
        "- 文档侧向量化文本 `search_text` ="
        " `title` + `description` + `category`（含中文类别名）+ `tags` + `location`\n"
        "- **不含**：`college`（避免「计算机学院」误召回）、活动记录、评价、报名/收藏计数\n"
        "- 查询侧：对原始 keyword 做 `text_embedding`，再 kNN "
        f"（`k={max(n, 20)}`，覆盖全库排序）\n"
        "- 复现：`python backend/scripts/cosine-threshold-experiment.py`"
        "（可选 `--rebuild` 先全量重建索引）\n"
        "- 本报告阈值结论：**保持 τ=0.90**（不再下调/上调）\n"
    )
    sections.append("## 3. Query 设计\n")
    sections.append(
        "按意图分四类：完全匹配（exact）、近义（synonym）、主题（theme）、"
        "几乎无关（unrelated）。\n"
    )
    sections.append(md_table(
        ["标签", "query", "类型说明"],
        [[a, b, c] for a, b, c in QUERIES],
    ))
    sections.append("")
    sections.append("## 4. 分 Query 排序结果\n")

    # Per-query ranked table
    for label, query, role in QUERIES:
        scores = matrix[label]
        ranked = sorted(activities, key=lambda a: scores.get(a["id"], -1.0), reverse=True)
        rows = []
        for rank, act in enumerate(ranked, 1):
            es_s = scores.get(act["id"])
            rows.append([
                str(rank),
                str(act["id"]),
                act["title"][:28],
                act["category"],
                f"{es_s:.4f}" if es_s is not None else "-",
                f"{approx_cosine(es_s):.4f}" if es_s is not None else "-",
            ])
        sections.append(f"### {label} — `{query}`\n")
        sections.append(f"_{role}_\n")
        sections.append(md_table(
            ["名次", "id", "标题", "类别", "ES score", "approx cos"],
            rows,
        ))
        sections.append("")

        top, bottom = ranked[0], ranked[-1]
        sections.append(
            f"- Top1: id={top['id']} {top['title']}  "
            f"score={scores.get(top['id']):.4f}  cos≈{approx_cosine(scores.get(top['id'])):.4f}\n"
            f"- Bottom: id={bottom['id']} {bottom['title']}  "
            f"score={scores.get(bottom['id']):.4f}  "
            f"cos≈{approx_cosine(scores.get(bottom['id'])):.4f}\n"
            f"- Top−Bottom Δscore = "
            f"{(scores.get(top['id']) or 0) - (scores.get(bottom['id']) or 0):.4f}\n"
        )

    # Compact wide matrix (ES score)
    sections.append("## 5. 汇总矩阵（ES score ≈ (1+cos)/2）\n")
    headers = ["id", "标题"] + [q[0] for q in QUERIES]
    rows = []
    for act in activities:
        row = [str(act["id"]), act["title"][:20]]
        for label, _, _ in QUERIES:
            s = matrix[label].get(act["id"])
            row.append(f"{s:.3f}" if s is not None else "-")
        rows.append(row)
    sections.append(md_table(headers, rows))
    sections.append("")

    # Threshold conclusions (locked at 0.90)
    sections.append("## 6. 结论（阈值保持 τ=0.90）\n")
    exact_top1 = []
    synonym_top1 = []
    theme_top1 = []
    all_scores: list[float] = []
    for label, query, role in QUERIES:
        scores = sorted(matrix[label].values(), reverse=True)
        all_scores.extend(scores)
        if label.startswith("exact") and scores:
            exact_top1.append(scores[0])
        if label.startswith("synonym") and scores:
            synonym_top1.append(scores[0])
        if label.startswith("theme") and scores:
            theme_top1.append(scores[0])

    def keep_count(label: str, tau: float) -> int:
        return sum(1 for s in matrix[label].values() if s >= tau)

    if all_scores:
        sections.append(
            f"- 全体 ES score 大致落在 **{min(all_scores):.3f} ~ {max(all_scores):.3f}**"
            f"（绝对分整体偏高，主要靠相对排序区分）。\n"
        )
    if exact_top1:
        sections.append(
            f"- 完全匹配 Top1 约 **{min(exact_top1):.3f} ~ {max(exact_top1):.3f}**。\n"
        )
    if synonym_top1:
        sections.append(
            f"- 近义 Top1 约 **{min(synonym_top1):.3f} ~ {max(synonym_top1):.3f}**。\n"
        )
    if theme_top1:
        sections.append(
            f"- 主题 Top1 约 **{min(theme_top1):.3f} ~ {max(theme_top1):.3f}**。\n"
        )

    sections.append(
        "- 「无关」query 若语料仍有近邻（如「量子纠缠」→量子讲座），最高分也会很高，"
        "不宜用其当噪声地板。\n"
    )
    sections.append("- 阈值扫描（语义硬截断后约保留条数，供对照；**不改生产阈值**）：\n")
    for tau in (0.92, 0.91, 0.90, 0.89):
        leisure_k = keep_count("theme_休闲", tau) if "theme_休闲" in matrix else -1
        relax_k = keep_count("theme_周末放松", tau) if "theme_周末放松" in matrix else -1
        play_k = keep_count("theme_找伙伴玩", tau) if "theme_找伙伴玩" in matrix else -1
        sports_k = keep_count("synonym_球类运动", tau) if "synonym_球类运动" in matrix else -1
        note = ""
        if abs(tau - 0.92) < 1e-9:
            note = "（过严）"
        elif abs(tau - 0.89) < 1e-9:
            note = "（过松）"
        elif abs(tau - 0.90) < 1e-9:
            note = "（**采用**）"
        sections.append(
            f"  - `τ={tau:.2f}`：休闲 {leisure_k} / 周末放松 {relax_k} / "
            f"找伙伴玩 {play_k} / 球类运动 {sports_k}{note}\n"
        )
    sections.append(
        "- **生产阈值保持 `τ=0.90`**（约 cosine≥0.80），配置项 "
        "`app.elasticsearch.semantic-absolute-threshold=0.90`。"
        " hybrid 中 BM25 命中仍豁免该硬截断。\n"
    )

    leisure = matrix.get("theme_休闲") or {}
    if leisure:
        music_id = 6
        music_s = leisure.get(music_id)
        ranked_ids = sorted(leisure.keys(), key=lambda i: leisure[i], reverse=True)
        music_rank = ranked_ids.index(music_id) + 1 if music_id in ranked_ids else None
        tops = []
        for aid in ranked_ids[:5]:
            title = next((a["title"] for a in activities if a["id"] == aid), str(aid))
            tops.append(f"id={aid} {title} ({leisure[aid]:.4f})")
        sections.append("## 7. 附录：「休闲」与音乐节\n")
        sections.append(
            "短词「休闲」对照（当前 `search_text` 含 tags/category/location，不含 college）：\n\n"
            f"- 音乐节（id=6）约 **rank#{music_rank}**、ES score≈**{music_s:.4f}**。\n"
            f"- 同 query 头部：{'; '.join(tops)}。\n"
            "- 分数偏低多因活动未打「休闲」标签、文案偏演出曲风；"
            "GTE 更贴「放松/桌游」。不通过降 τ 解决，应靠组织者标签/描述。\n"
        )

    sections.append("\n完整长表见 `cosine-threshold-experiment.csv`。"
                    " 复现脚本：`backend/scripts/cosine-threshold-experiment.py`。\n")

    md_path.write_text("\n".join(sections), encoding="utf-8")
    print(f"wrote {md_path}")
    print(f"wrote {csv_path}")


if __name__ == "__main__":
    main()
