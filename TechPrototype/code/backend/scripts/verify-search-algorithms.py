#!/usr/bin/env python3
"""Verify semantic threshold filter and composite ranking via live API."""

import json
import urllib.parse
import urllib.request
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any

BASE = "http://localhost:8080/api/v1"
REPORT_DIR = Path(__file__).resolve().parents[2] / "report"


def post_json(path: str, body: dict) -> dict:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE}{path}",
        data=data,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def get_json(path: str, token: str) -> dict:
    req = urllib.request.Request(
        f"{BASE}{path}",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def search(token: str, **params: Any) -> dict:
    qs = urllib.parse.urlencode(params, quote_via=urllib.parse.quote)
    return get_json(f"/search/activities?{qs}", token)


def hit_row(item: dict) -> dict:
    return {
        "id": item.get("id"),
        "title": item.get("title"),
        "keywordScore": item.get("keywordScore"),
        "semanticScore": item.get("semanticScore"),
        "searchScore": item.get("searchScore"),
        "compositeScore": item.get("compositeScore"),
        "signupCount": item.get("signupCount"),
        "favoriteCount": item.get("favoriteCount"),
        "hot": (item.get("signupCount") or 0) + (item.get("favoriteCount") or 0),
        "searchChannel": item.get("searchChannel"),
    }


def run_case(token: str, name: str, **params: Any) -> dict:
    resp = search(token, size=20, **params)
    data = resp.get("data") or {}
    content = data.get("content") or data.get("items") or []
    total = data.get("totalElements", data.get("total", len(content)))
    return {
        "name": name,
        "params": params,
        "total": total,
        "ids": [x.get("id") for x in content],
        "hits": [hit_row(x) for x in content],
    }


def main() -> None:
    login = post_json("/auth/login", {"userId": "524030910001", "password": "123456"})
    token = login["data"]["token"]

    cases = [
        run_case(token, "A1_羽毛球_keyword", keyword="羽毛球", mode="keyword", sort="relevance"),
        run_case(token, "A2_羽毛球_semantic", keyword="羽毛球", mode="semantic", sort="relevance"),
        run_case(token, "A3_羽毛球_hybrid", keyword="羽毛球", mode="hybrid", sort="relevance"),
        run_case(token, "B1_周末放松_keyword", keyword="周末放松", mode="keyword", sort="relevance"),
        run_case(token, "B2_周末放松_semantic", keyword="周末放松", mode="semantic", sort="relevance"),
        run_case(token, "B3_周末放松_hybrid", keyword="周末放松", mode="hybrid", sort="relevance"),
        run_case(token, "C1_量子物理_hybrid", keyword="量子物理", mode="hybrid", sort="relevance"),
        run_case(token, "D1_周末放松_relevance", keyword="周末放松", mode="hybrid", sort="relevance"),
        run_case(token, "D2_周末放松_hot", keyword="周末放松", mode="hybrid", sort="hot"),
        run_case(token, "D3_周末放松_composite_0.7", keyword="周末放松", mode="hybrid", sort="composite", matchWeight=0.7),
        run_case(token, "D4_周末放松_composite_1.0", keyword="周末放松", mode="hybrid", sort="composite", matchWeight=1.0),
        run_case(token, "D5_周末放松_composite_0.0", keyword="周末放松", mode="hybrid", sort="composite", matchWeight=0.0),
        run_case(token, "E1_activities接口_羽毛球_composite", keyword="羽毛球", sort="composite", matchWeight=0.5),
    ]

    # activities endpoint uses different path
    qs = urllib.parse.urlencode(
        {"keyword": "羽毛球", "sort": "composite", "matchWeight": 0.5, "pageSize": 20},
        quote_via=urllib.parse.quote,
    )
    act_resp = get_json(f"/activities?{qs}", token)
    act_data = act_resp.get("data") or {}
    act_content = act_data.get("content") or act_data.get("items") or []
    cases[-1] = {
        "name": "E1_activities接口_羽毛球_composite",
        "params": {"keyword": "羽毛球", "sort": "composite", "matchWeight": 0.5},
        "total": act_data.get("totalElements", len(act_content)),
        "ids": [x.get("id") for x in act_content],
        "hits": [hit_row(x) for x in act_content],
    }

    out = {"cases": cases}
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = REPORT_DIR / "search-verify-results.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"wrote {out_path}")

    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
