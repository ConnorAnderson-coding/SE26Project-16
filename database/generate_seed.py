#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
根据 activity_catalog.py 中的手写词条生成 seed.sql。

原则：
- 每条活动的标题 / 描述 / 地点 / 标签来自同一词条，禁止拆开重组；
- 仅对日期、组织者、学院、报名热度与「第 N 期」做变体；
- 用户兴趣只从同一兴趣簇内抽样。
"""

from __future__ import annotations

import importlib.util
import json
import random
from datetime import datetime, timedelta
from pathlib import Path

RNG = random.Random(20260717)
HERE = Path(__file__).resolve().parent

PASSWORD_HASH = "$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci"

COLLEGES = [
    "软件学院", "计算机学院", "信息学院", "电子学院", "数学学院",
    "外国语学院", "机械学院", "生命学院", "经济管理学院", "人文学院",
]
GRADES = ["2022级", "2023级", "2024级", "2025级"]
AVAILABLE_TIMES = [
    "weekday_morning", "weekday_afternoon", "weekday_evening", "weekend",
]
SURNAMES = list(
    "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支柯昝管卢莫经房裘缪干解应宗丁宣贲邓郁单杭洪包诸左石崔吉钮龚程嵇邢滑裴陆荣翁荀羊於惠甄曲家封芮羿储靳汲邴糜松井段富巫乌焦巴弓牧隗山谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘钭厉戎祖武符刘景詹束龙叶幸司韶郜黎蓟薄印宿白怀蒲邰从鄂索咸籍赖卓蔺屠蒙池乔阴郁胥能苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍"
)
GIVEN_CHARS = list(
    "伟芳娜敏静丽强磊军洋勇艳杰涛超秀英华慧巧美静淑惠珠翠雅芝玉萍红娥玲芬芳燕彩春菊兰凤洁梅琳素云莲真环雪荣爱妹霞香月莺媛艳瑞凡佳嘉倩宁欣怡颖婷婧瑶瑾璇璐璟"
)
TEACHER_NAMES = [
    "王老师", "李老师", "张老师", "刘老师", "陈老师", "杨老师", "赵老师",
    "黄老师", "周老师", "吴老师", "徐老师", "孙老师", "胡老师", "朱老师",
    "高老师", "林老师", "何老师", "郭老师", "马老师", "罗老师",
]

# 适合加「第 N 期」的类别（社团训练、体育周训、志愿常态等）
RECURRING_CATEGORIES = {"club", "sports", "volunteer"}

FEEDBACK_BY_CATEGORY = {
    "academic": [
        "讲座干货多，讲解清楚，提问环节也很充分。",
        "内容扎实，建议下次把讲义提前发到群里。",
        "学到了实用方法，适合相关专业同学。",
    ],
    "sports": [
        "组织有序，裁判公平，出完汗很爽。",
        "运动氛围好，下次还会报名。",
        "场地安排合理，建议多准备一些器材。",
    ],
    "club": [
        "氛围轻松，学到了不少技巧，认识了新朋友。",
        "零基础也能跟得上，材料准备很充分。",
        "社团活动很治愈，希望多办几期。",
    ],
    "arts": [
        "演出精彩，现场氛围很好。",
        "节目完成度高，音响效果不错。",
        "观演体验很好，期待下一场。",
    ],
    "volunteer": [
        "意义很大，居民反馈也好，值得参加。",
        "流程清楚，志愿时长认定及时。",
        "服务过程顺利，负责人很靠谱。",
    ],
    "innovation": [
        "动手环节很充实，导师点评中肯。",
        "组队体验好，对项目推进有帮助。",
        "节奏紧凑，学到了落地思路。",
    ],
}

RECORD_SUMMARIES = [
    "本次活动顺利结束，共有 {n} 人到场。物资已回收，场地清理完毕，感谢志愿者与工作人员。",
    "活动圆满落幕，签到约 {n} 人次。照片与反馈已整理归档，将据此优化下一期。",
    "本场达到预期目标，到场约 {n} 人，安全无事故。问卷建议已收集。",
]


def load_catalog():
    path = HERE / "activity_catalog.py"
    spec = importlib.util.spec_from_file_location("activity_catalog", path)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod.CATALOG, mod.INTEREST_CLUSTERS


def sql_str(s: str) -> str:
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_json(obj) -> str:
    return sql_str(json.dumps(obj, ensure_ascii=False, separators=(",", ":")))


def fmt_dt(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%d %H:%M:%S.000")


def random_name() -> str:
    return RNG.choice(SURNAMES) + "".join(
        RNG.choice(GIVEN_CHARS) for _ in range(RNG.randint(1, 2))
    )


def pick_interests(clusters) -> list[str]:
    cluster = list(RNG.choice(clusters))
    k = min(len(cluster), RNG.randint(2, min(4, len(cluster))))
    return RNG.sample(cluster, k)


def pick_available() -> list[str]:
    return RNG.sample(AVAILABLE_TIMES, RNG.randint(1, 3))


def make_users(n_students: int, n_teachers: int, clusters) -> list[dict]:
    users = [
        {
            "id": "524030910001",
            "name": "张三",
            "role": "student",
            "college": "软件学院",
            "grade": "2024级",
            "interests": ["AI", "摄影", "羽毛球"],
            "available_time": ["weekday_evening", "weekend"],
        },
        {
            "id": "524030910002",
            "name": "李四",
            "role": "student",
            "college": "计算机学院",
            "grade": "2023级",
            "interests": ["编程", "电竞", "篮球"],
            "available_time": ["weekend"],
        },
        {
            "id": "T001",
            "name": "王老师",
            "role": "teacher",
            "college": "软件学院",
            "grade": "教师",
            "interests": ["AI", "创业"],
            "available_time": ["weekday_morning", "weekday_afternoon"],
        },
        {
            "id": "admin001",
            "name": "系统管理员",
            "role": "admin",
            "college": "软件学院",
            "grade": "管理员",
            "interests": [],
            "available_time": [],
        },
    ]
    used = {u["id"] for u in users}

    for i in range(n_students):
        year = 522 + (i % 4)
        sid = f"{year}0309{20000 + i:05d}"
        assert sid not in used
        used.add(sid)
        users.append(
            {
                "id": sid,
                "name": random_name(),
                "role": "student",
                "college": RNG.choice(COLLEGES),
                "grade": GRADES[i % 4],
                "interests": pick_interests(clusters),
                "available_time": pick_available(),
            }
        )

    for i in range(n_teachers):
        tid = f"T{i + 2:03d}"
        used.add(tid)
        users.append(
            {
                "id": tid,
                "name": TEACHER_NAMES[i % len(TEACHER_NAMES)],
                "role": "teacher",
                "college": RNG.choice(COLLEGES),
                "grade": "教师",
                "interests": pick_interests(clusters)[:3],
                "available_time": pick_available(),
            }
        )
    return users


def instantiate_activity(
    aid: int,
    entry: tuple,
    organizers: list[str],
    base_day: datetime,
    cycle: int,
) -> dict:
    title, category, description, location, tags, max_p, duration_h, typical_hour = entry
    tags = list(tags)

    # 仅对常态活动加期次，避免讲座标题变得奇怪；标签绝不改动
    display_title = title
    if category in RECURRING_CATEGORIES and cycle > 0:
        display_title = f"{title}（第{cycle + 1}期）"
    elif category in {"academic", "innovation", "arts"} and cycle > 0:
        season = ["春季场", "暑期场", "秋季场", "冬季场"][cycle % 4]
        display_title = f"{title} · {season}"

    day_offset = -75 + (aid * 17 + cycle * 11) % 130
    start = base_day + timedelta(days=day_offset, hours=typical_hour)
    # 对齐到整点/半点
    start = start.replace(minute=0 if typical_hour < 20 else 30, second=0, microsecond=0)
    end = start + timedelta(hours=duration_h)

    now = datetime(2026, 7, 17, 12, 0, 0)
    if end < now - timedelta(days=2):
        status = "ended"
    elif start > now + timedelta(days=50):
        status = RNG.choices(["published", "draft"], weights=[0.92, 0.08])[0]
    else:
        status = RNG.choices(["published", "draft", "ended"], weights=[0.88, 0.04, 0.08])[0]

    if status == "draft":
        signup = RNG.randint(0, 3)
        fav = RNG.randint(0, 5)
        view = RNG.randint(5, 40)
        check_in = 0
    else:
        signup = min(max_p, int(max_p * RNG.uniform(0.25, 0.9)))
        fav = max(1, int(signup * RNG.uniform(0.25, 0.7)))
        view = signup * RNG.randint(4, 10) + RNG.randint(20, 180)
        check_in = int(signup * RNG.uniform(0.55, 0.95)) if status == "ended" else 0

    hotness = round(
        signup * 0.4 + fav * 0.3 + view * 0.01 + check_in * 0.2 + RNG.uniform(0, 3), 2
    )
    code_prefix = {
        "academic": "AC",
        "sports": "SP",
        "club": "CL",
        "arts": "AR",
        "volunteer": "VO",
        "innovation": "IN",
    }[category]

    return {
        "id": aid,
        "title": display_title[:200],
        "category": category,
        "description": description,
        "start_time": start,
        "end_time": end,
        "location": location,
        "organizer_id": RNG.choice(organizers),
        "college": RNG.choice(COLLEGES),
        "poster": f"https://picsum.photos/seed/act{aid}/800/400",
        "max_participants": max_p,
        "signup_count": signup,
        "favorite_count": fav,
        "view_count": view,
        "check_in_count": check_in,
        "hotness_score": hotness,
        "status": status,
        "tags": tags,
        "check_in_code": f"{code_prefix}{aid:04d}",
    }


def write_seed(
    path: Path,
    n_students: int = 800,
    n_teachers: int = 40,
    n_activities: int = 1500,
    n_registrations: int = 5500,
    n_favorites: int = 2200,
    n_feedback: int = 1200,
) -> dict:
    catalog, clusters = load_catalog()
    assert catalog, "activity_catalog.CATALOG is empty"

    users = make_users(n_students, n_teachers, clusters)
    student_ids = [u["id"] for u in users if u["role"] == "student"]
    teacher_ids = [u["id"] for u in users if u["role"] == "teacher"]
    organizers = student_ids[:220] + teacher_ids
    participant_pool = [u["id"] for u in users if u["role"] != "admin"]

    base_day = datetime(2026, 7, 17, 0, 0, 0)
    activities: list[dict] = []
    for i in range(n_activities):
        entry = catalog[i % len(catalog)]
        cycle = i // len(catalog)
        activities.append(
            instantiate_activity(i + 1, entry, organizers, base_day, cycle)
        )

    # 演示活动：按标题从词条库整条覆盖（标题/描述/地点/标签一并替换，禁止半截覆盖）
    demo_titles = [
        "AI 与大模型技术前沿讲座",
        "校园羽毛球友谊赛",
        "摄影社户外采风活动",
        "程序设计竞赛训练营",
        "校园志愿者招募 — 社区服务日",
        "校园音乐节 — 夏日之声",
        "三人篮球校园联赛",
        "量子信息与量子计算导论",
        "烘焙社 — 周末手工饼干工作坊",
        "校际辩论邀请赛选拔",
        "大学生创业路演夜",
        "心理健康工作坊 — 压力与放松",
        "书法社团钢笔字入门课",
        "机器人兴趣小组调试日",
        "英语角 — Movie Night 观影畅谈",
        "校园趣味马拉松 5 公里",
        "桌游社周末解压局",
        "有机化学光谱表征开放日",
    ]
    catalog_by_title = {e[0]: e for e in catalog}
    demo_fixed = {
        1: {
            "organizer_id": "T001",
            "college": "软件学院",
            "start_time": datetime(2026, 7, 15, 14, 0),
            "end_time": datetime(2026, 7, 15, 16, 0),
            "status": "published",
            "check_in_code": "AI2026",
            "poster": "https://picsum.photos/seed/ai-lecture/800/400",
        },
        2: {
            "organizer_id": "524030910002",
            "college": "计算机学院",
            "start_time": datetime(2026, 7, 20, 9, 0),
            "end_time": datetime(2026, 7, 20, 12, 0),
            "status": "published",
            "check_in_code": "BD2026",
            "poster": "https://picsum.photos/seed/badminton/800/400",
        },
        3: {
            "organizer_id": "524030910001",
            "college": "软件学院",
            "start_time": datetime(2026, 7, 18, 8, 0),
            "end_time": datetime(2026, 7, 18, 17, 0),
            "status": "published",
            "check_in_code": "PH2026",
            "poster": "https://picsum.photos/seed/photo-club/800/400",
        },
    }
    for idx, title in enumerate(demo_titles):
        entry = catalog_by_title.get(title)
        if entry is None:
            raise SystemExit(f"demo title missing in catalog: {title}")
        aid = idx + 1
        rebuilt = instantiate_activity(aid, entry, organizers, base_day, cycle=0)
        rebuilt["title"] = title  # 演示条不加「第 N 期 / 季节场」
        if aid in demo_fixed:
            rebuilt.update(demo_fixed[aid])
        activities[aid - 1] = rebuilt

    published = [a for a in activities if a["status"] in ("published", "ended")]

    reg_pairs: set[tuple[int, str]] = set()
    registrations = []
    rid = 1
    attempts = 0
    while len(registrations) < n_registrations and attempts < n_registrations * 25:
        attempts += 1
        act = RNG.choice(published)
        uid = RNG.choice(participant_pool)
        if (act["id"], uid) in reg_pairs or uid == act["organizer_id"]:
            continue
        reg_pairs.add((act["id"], uid))
        if act["status"] == "ended":
            status = RNG.choices(["approved", "rejected", "pending"], weights=[0.86, 0.07, 0.07])[0]
        else:
            status = RNG.choices(["approved", "pending", "rejected"], weights=[0.72, 0.2, 0.08])[0]
        created = act["start_time"] - timedelta(days=RNG.randint(1, 18), hours=RNG.randint(0, 20))
        registrations.append(
            {
                "id": rid,
                "activity_id": act["id"],
                "user_id": uid,
                "status": status,
                "created_at": created,
            }
        )
        rid += 1

    approved_by_act: dict[int, int] = {}
    for r in registrations:
        if r["status"] == "approved":
            approved_by_act[r["activity_id"]] = approved_by_act.get(r["activity_id"], 0) + 1
    for a in activities:
        if a["id"] in approved_by_act:
            a["signup_count"] = min(a["max_participants"], max(a["signup_count"], approved_by_act[a["id"]]))

    fav_pairs: set[tuple[str, int]] = set()
    favorites = []
    attempts = 0
    while len(favorites) < n_favorites and attempts < n_favorites * 25:
        attempts += 1
        uid = RNG.choice(participant_pool)
        act = RNG.choice(published)
        if (uid, act["id"]) in fav_pairs:
            continue
        fav_pairs.add((uid, act["id"]))
        favorites.append({"user_id": uid, "activity_id": act["id"]})
    for a in activities:
        cnt = sum(1 for f in favorites if f["activity_id"] == a["id"])
        if cnt:
            a["favorite_count"] = max(a["favorite_count"], cnt)

    ended = [a for a in activities if a["status"] == "ended"]
    record_acts = RNG.sample(ended, k=min(len(ended), max(1, int(len(ended) * 0.55))))
    records = []
    for a in record_acts:
        n = max(a["check_in_count"], RNG.randint(8, 50))
        records.append(
            {
                "activity_id": a["id"],
                "summary": RNG.choice(RECORD_SUMMARIES).format(n=n),
                "photos": [
                    f"https://picsum.photos/seed/rec{a['id']}a/400/300",
                    f"https://picsum.photos/seed/rec{a['id']}b/400/300",
                ],
                "published_at": a["end_time"] + timedelta(hours=RNG.randint(2, 36)),
            }
        )

    approved_map: dict[int, list[str]] = {}
    for r in registrations:
        if r["status"] == "approved":
            approved_map.setdefault(r["activity_id"], []).append(r["user_id"])
    act_by_id = {a["id"]: a for a in activities}

    feedback = []
    fb_pairs: set[tuple[int, str]] = set()
    fb_id = 1
    targets = [a for a in activities if a["id"] in approved_map and a["status"] in ("ended", "published")]
    attempts = 0
    while len(feedback) < n_feedback and attempts < n_feedback * 40:
        attempts += 1
        if not targets:
            break
        act = RNG.choice(targets)
        uid = RNG.choice(approved_map[act["id"]])
        if (act["id"], uid) in fb_pairs:
            continue
        fb_pairs.add((act["id"], uid))
        comments = FEEDBACK_BY_CATEGORY.get(act["category"], FEEDBACK_BY_CATEGORY["club"])
        feedback.append(
            {
                "id": fb_id,
                "activity_id": act["id"],
                "user_id": uid,
                "rating": RNG.choices([5, 4, 3, 2, 1], weights=[0.48, 0.34, 0.12, 0.04, 0.02])[0],
                "content": RNG.choice(comments),
                "created_at": (
                    act["end_time"] + timedelta(hours=RNG.randint(1, 48))
                    if act["status"] == "ended"
                    else act["start_time"] + timedelta(hours=RNG.randint(1, 4))
                ),
            }
        )
        fb_id += 1

    # 演示账号保底报名/收藏
    demo = "524030910001"
    for aid in range(1, 9):
        a = act_by_id[aid]
        if a["organizer_id"] != demo and (aid, demo) not in reg_pairs:
            registrations.append(
                {
                    "id": rid,
                    "activity_id": aid,
                    "user_id": demo,
                    "status": "approved",
                    "created_at": datetime(2026, 7, 1, 10, 0),
                }
            )
            reg_pairs.add((aid, demo))
            rid += 1
        if (demo, aid) not in fav_pairs:
            favorites.append({"user_id": demo, "activity_id": aid})
            fav_pairs.add((demo, aid))

    lines: list[str] = [
        "SET NAMES utf8mb4;",
        "",
        "USE campus_activity;",
        "",
        "-- Generated from hand-crafted activity_catalog.py (coherent title/tags/location).",
        "-- Password for all demo users: 123456",
        "",
    ]

    def batch_insert(header: str, rows: list[str], batch_size: int = 200) -> None:
        for i in range(0, len(rows), batch_size):
            batch = rows[i : i + batch_size]
            lines.append(header)
            lines.append(",\n".join(batch) + ";")
            lines.append("")

    user_rows = [
        f"({sql_str(u['id'])}, {sql_str(PASSWORD_HASH)}, {sql_str(u['name'])}, "
        f"{sql_str(u['role'])}, {sql_str(u['college'])}, {sql_str(u['grade'])}, "
        f"{sql_json(u['interests'])}, {sql_json(u['available_time'])})"
        for u in users
    ]
    batch_insert(
        "INSERT INTO `user` (id, password_hash, name, role, college, grade, interests, available_time) VALUES",
        user_rows,
    )

    act_rows = [
        f"({a['id']}, {sql_str(a['title'])}, {sql_str(a['category'])}, {sql_str(a['description'])}, "
        f"{sql_str(fmt_dt(a['start_time']))}, {sql_str(fmt_dt(a['end_time']))}, "
        f"{sql_str(a['location'])}, {sql_str(a['organizer_id'])}, {sql_str(a['college'])}, "
        f"{sql_str(a['poster'])}, {a['max_participants']}, {a['signup_count']}, {a['favorite_count']}, "
        f"{a['view_count']}, {a['check_in_count']}, {a['hotness_score']}, {sql_str(a['status'])}, "
        f"{sql_json(a['tags'])}, {sql_str(a['check_in_code'])})"
        for a in activities
    ]
    batch_insert(
        "INSERT INTO activity (id, title, category, description, start_time, end_time, location, "
        "organizer_id, college, poster, max_participants, signup_count, favorite_count, "
        "view_count, check_in_count, hotness_score, status, tags, check_in_code) VALUES",
        act_rows,
        batch_size=80,
    )

    batch_insert(
        "INSERT INTO registration (id, activity_id, user_id, status, created_at) VALUES",
        [
            f"({r['id']}, {r['activity_id']}, {sql_str(r['user_id'])}, {sql_str(r['status'])}, "
            f"{sql_str(fmt_dt(r['created_at']))})"
            for r in registrations
        ],
    )
    batch_insert(
        "INSERT INTO favorite (user_id, activity_id) VALUES",
        [f"({sql_str(f['user_id'])}, {f['activity_id']})" for f in favorites],
    )
    if records:
        batch_insert(
            "INSERT INTO activity_record (activity_id, summary, photos, published_at) VALUES",
            [
                f"({r['activity_id']}, {sql_str(r['summary'])}, {sql_json(r['photos'])}, "
                f"{sql_str(fmt_dt(r['published_at']))})"
                for r in records
            ],
            batch_size=100,
        )
    if feedback:
        batch_insert(
            "INSERT INTO feedback (id, activity_id, user_id, rating, content, created_at) VALUES",
            [
                f"({f['id']}, {f['activity_id']}, {sql_str(f['user_id'])}, {f['rating']}, "
                f"{sql_str(f['content'])}, {sql_str(fmt_dt(f['created_at']))})"
                for f in feedback
            ],
        )

    lines.append(f"ALTER TABLE activity AUTO_INCREMENT = {n_activities + 1};")
    lines.append(f"ALTER TABLE registration AUTO_INCREMENT = {len(registrations) + 1};")
    lines.append(f"ALTER TABLE feedback AUTO_INCREMENT = {len(feedback) + 1};")
    lines.append("")

    path.write_text("\n".join(lines), encoding="utf-8")
    return {
        "catalog_unique": len(catalog),
        "users": len(users),
        "activities": len(activities),
        "registrations": len(registrations),
        "favorites": len(favorites),
        "activity_records": len(records),
        "feedback": len(feedback),
        "total": (
            len(users)
            + len(activities)
            + len(registrations)
            + len(favorites)
            + len(records)
            + len(feedback)
        ),
    }


def main() -> None:
    out = HERE / "seed.sql"
    stats = write_seed(out)
    print(f"Wrote {out}")
    for k, v in stats.items():
        print(f"  {k}: {v}")


if __name__ == "__main__":
    main()
