#!/usr/bin/env python3
"""Generate database/seed.sql with ~10k+ realistic campus activity records."""

from __future__ import annotations

import json
import random
from datetime import datetime, timedelta
from pathlib import Path

RNG = random.Random(20260717)

PASSWORD_HASH = "$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci"

COLLEGES = [
    "软件学院",
    "计算机学院",
    "信息学院",
    "电子学院",
    "数学学院",
    "外国语学院",
    "机械学院",
    "生命学院",
    "经济管理学院",
    "人文学院",
]

GRADES = ["2022级", "2023级", "2024级", "2025级"]

INTERESTS = [
    "AI", "摄影", "羽毛球", "篮球", "编程", "音乐", "舞蹈", "志愿服务",
    "创业", "阅读", "电竞", "旅行", "足球", "网球", "跑步", "游泳",
    "辩论", "英语", "书法", "绘画", "机器人", "化学", "物理", "数学建模",
    "桌游", "烘焙", "心理", "电影", "话剧", "公益",
]

AVAILABLE_TIMES = [
    "weekday_morning",
    "weekday_afternoon",
    "weekday_evening",
    "weekend",
]

SURNAMES = list("赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支柯昝管卢莫经房裘缪干解应宗丁宣贲邓郁单杭洪包诸左石崔吉钮龚程嵇邢滑裴陆荣翁荀羊於惠甄曲家封芮羿储靳汲邴糜松井段富巫乌焦巴弓牧隗山谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘钭厉戎祖武符刘景詹束龙叶幸司韶郜黎蓟薄印宿白怀蒲邰从鄂索咸籍赖卓蔺屠蒙池乔阴郁胥能苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍")

GIVEN_CHARS = list(
    "伟芳娜敏静丽强磊军洋勇艳杰涛超秀英华慧巧美娜静淑惠珠翠雅芝玉萍红娥玲芬芳燕彩春菊兰凤洁梅琳素云莲真环雪荣爱妹霞香月莺媛艳瑞凡佳嘉倩宁欣怡颖婷婧瑶瑾璇璐璟"
)

LOCATIONS = [
    "软件大楼 A101", "软件大楼 B203", "计算机楼 302 实验室", "信息楼报告厅",
    "理科楼报告厅", "教学楼 B203", "教学楼 C105", "文科楼书画室",
    "体育馆羽毛球场", "体育馆篮球馆", "室外篮球场", "田径场",
    "游泳馆", "网球场", "中心广场", "图书馆前广场",
    "学生活动中心多功能厅", "学生活动中心厨房", "学生宿舍区活动室",
    "心理中心团体室", "国际交流中心", "创业孵化中心",
    "工训中心 B区", "化学楼 2 号实验室", "物理楼演示厅",
    "音乐厅", "大礼堂", "校门口集合", "东门广场", "西区咖啡厅",
    "南区食堂二楼", "北区报告厅", "创新工场", "媒体中心演播室",
]

# category -> (titles templates, description templates, tag pools)
ACTIVITY_TEMPLATES: dict[str, dict] = {
    "academic": {
        "titles": [
            "{topic}前沿讲座",
            "{topic}专题研讨会",
            "{topic}读书分享会",
            "{topic}公开课：从入门到实践",
            "{topic}学术沙龙",
            "名师讲坛 · {topic}",
            "{topic}文献精读工作坊",
            "{topic}科研方法分享",
            "跨学科对话：{topic}",
            "{topic}期末复习答疑专场",
        ],
        "topics": [
            "大模型与软件工程", "机器学习可解释性", "数据库系统内核", "计算机网络协议",
            "编译原理实践", "操作系统虚拟化", "信息检索与推荐", "自然语言处理",
            "计算机视觉", "分布式系统一致性", "密码学与隐私计算", "量子信息入门",
            "微分几何直觉", "概率统计在工程中的应用", "数值分析与科学计算",
            "有机化学光谱表征", "凝聚态物理前沿", "生物医学影像", "认知科学导论",
            "跨文化交际理论", "当代文学批评", "宏观经济学热点", "供应链优化",
            "人机交互设计", "软件测试与质量保障", "开源社区协作", "学术写作与投稿",
            "科研伦理与学术规范", "数学建模案例复盘", "算法复杂度分析",
        ],
        "descs": [
            "本次活动邀请校内外学者与业界嘉宾，围绕{topic}展开系统讲解，结合最新论文与落地案例，适合相关专业及跨院系同学参与。活动含提问互动环节，欢迎提前准备问题。",
            "面向本科生与研究生，介绍{topic}的基本概念、常用方法与近期研究进展。现场提供讲义提纲，建议自备笔记本。名额有限，请按时签到入场。",
            "通过精选案例与小组讨论，帮助同学建立对{topic}的整体认知，并分享课题组与实验室的实践经验。适合希望拓展视野、寻找科研方向的同学。",
            "聚焦{topic}中的关键问题与常见误区，嘉宾将结合项目经历拆解思路。活动结束后可自愿加入相关学习群，持续交流。",
        ],
        "tags": [
            ["AI", "编程"], ["学术", "阅读"], ["物理", "数学建模"], ["化学", "实验"],
            ["英语", "表达"], ["辩论", "表达"], ["编程", "AI"], ["学术", "AI"],
            ["阅读", "学术"], ["创业", "创新"],
        ],
    },
    "sports": {
        "titles": [
            "校园{sport}友谊赛",
            "{sport}俱乐部周末训练营",
            "院际{sport}挑战赛",
            "{sport}入门体验课",
            "夏日{sport}联赛小组赛",
            "{sport}技巧提升工作坊",
            "趣味{sport}嘉年华",
            "师生{sport}交流赛",
            "{sport}体能强化训练",
            "夜场{sport}开放日",
        ],
        "topics": [
            "羽毛球", "篮球", "足球", "网球", "乒乓球", "排球", "跑步", "游泳",
            "三人篮球", "毽球", "飞盘", "攀岩", "瑜伽", "太极", "健美操", "骑行",
        ],
        "descs": [
            "面向全校师生的{topic}活动，按水平分组，重在参与与交流。请穿着合适运动装备，提前热身；现场提供基础器材，优胜者可获纪念奖品。",
            "由体育俱乐部组织的{topic}训练与比赛，教练现场指导动作要领。适合零基础到进阶选手，欢迎结伴报名。注意补水与防晒。",
            "以学院为单位组队参加{topic}赛事，赛制公平透明，裁判由体育部指派。报名后请关注群内分组通知与赛程安排。",
            "轻松氛围下的{topic}体验活动，现场有热身游戏与技巧演示。完赛或参与满时长可领取运动打卡贴纸。",
        ],
        "tags": [
            ["羽毛球", "体育运动"], ["篮球", "体育运动"], ["足球", "体育运动"],
            ["跑步", "体育运动"], ["游泳", "体育运动"], ["网球", "体育运动"],
            ["体育运动", "休闲"], ["跑步", "旅行"],
        ],
    },
    "club": {
        "titles": [
            "{club}主题活动日",
            "{club}周末工作坊",
            "{club}新人见面会",
            "{club}技能分享会",
            "{club}户外实践",
            "{club}联谊派对",
            "{club}成果展示会",
            "{club}兴趣体验课",
            "{club}夜谈沙龙",
            "{club}学期招新宣讲",
        ],
        "topics": [
            "摄影社", "烘焙社", "桌游社", "英语角", "读书会", "电影社",
            "动漫社", "手工社", "天文社", "徒步社", "茶艺社", "园艺社",
            "心理学社", "辩论社", "模特社", "滑板社", "飞盘社", "棋类社",
        ],
        "descs": [
            "{topic}邀请新旧社员共同参与，现场有破冰游戏与主题体验环节。零基础友好，材料部分现场提供，欢迎带上朋友一起加入。",
            "本场活动由{topic}骨干学长学姐带队，讲解基础技巧并组织小组实践。结束后可自愿提交作品参加社内评选。",
            "轻松的社团氛围，围绕{topic}兴趣主题展开交流与动手实践。适合想认识同好、丰富课余生活的同学。",
            "{topic}定期活动，包含短分享、自由练习与合影留念。请提前在群内确认地点变更与天气预案。",
        ],
        "tags": [
            ["摄影", "艺术"], ["烘焙", "休闲"], ["桌游", "休闲"], ["英语", "交流"],
            ["阅读", "学术"], ["电影", "休闲"], ["心理", "放松"], ["旅行", "摄影"],
            ["舞蹈", "音乐"], ["电竞", "休闲"],
        ],
    },
    "arts": {
        "titles": [
            "校园{art}专场演出",
            "{art}工作坊 · 入门体验",
            "社团联合会 · {art}展演夜",
            "{art}原创作品征集发布会",
            "周末{art}公开课",
            "{art}文化交流晚会",
            "青春{art}嘉年华",
            "{art}大师课观摩",
            "宿舍区{art}快闪活动",
            "{art}期末汇报演出",
        ],
        "topics": [
            "合唱", "民谣弹唱", "街舞", "古典舞", "话剧", "相声小品",
            "书法", "水彩画", "油画写生", "钢琴独奏", "吉他弹唱", "乐队巡演",
            "摄影展览", "短视频创作", "配音演绎", "戏曲欣赏",
        ],
        "descs": [
            "呈现校园原创与经典改编的{topic}节目，现场互动抽奖与观众投票。请提前入场占座，遵守摄影礼仪，共同维护观演秩序。",
            "专业老师与社团骨干带领同学体验{topic}基础技法，提供部分道具与材料。适合想培养审美与表达能力的同学。",
            "以{topic}为主题的文艺活动，涵盖展示、教学与合影环节。欢迎各院系文艺爱好者报名演出或当观众。",
            "沉浸式{topic}体验，现场有指导与作品点评。结束后优秀作品将有机会在校园媒体平台展示。",
        ],
        "tags": [
            ["音乐", "文艺"], ["舞蹈", "文艺"], ["书法", "文艺"], ["绘画", "艺术"],
            ["摄影", "艺术"], ["话剧", "表达"], ["音乐", "舞蹈"], ["电影", "文艺"],
        ],
    },
    "volunteer": {
        "titles": [
            "志愿行动 · {cause}",
            "社区服务日 — {cause}",
            "公益实践：{cause}",
            "周末志愿小队 · {cause}",
            "暑期社会实践 — {cause}",
            "爱心接力：{cause}",
            "校园公益周 · {cause}",
            "结对帮扶活动 — {cause}",
            "环保行动日 · {cause}",
            "志愿时数认定活动 · {cause}",
        ],
        "topics": [
            "敬老院陪伴", "社区环境清洁", "图书馆图书整理", "校园文明劝导",
            "儿童课业辅导", "流浪动物救助宣传", "无偿献血志愿者", "交通安全宣传",
            "垃圾分类宣讲", "助残日陪伴", "乡村振兴调研协助", "防灾演练志愿者",
            "运动会后勤保障", "迎新志愿者服务", "考试周自习室维护", "食堂文明劝导",
        ],
        "descs": [
            "组织同学开展{topic}相关志愿服务，可按规定计入志愿服务时长。请穿着舒适、准时集合，服从现场负责人安排，注意安全与礼貌沟通。",
            "面向热心公益的师生招募，完成本次{topic}任务后可获得志愿证明与纪念品。欢迎首次参加志愿活动的同学。",
            "与社区/校内部门合作开展{topic}，强调尊重服务对象与团队协作。活动前后有简短培训与复盘分享。",
            "短时高效的{topic}志愿项目，分组执行、组长带队。报名成功后请保持手机畅通，关注临时调配通知。",
        ],
        "tags": [
            ["志愿服务"], ["公益", "志愿服务"], ["志愿服务", "旅行"],
            ["公益", "阅读"], ["志愿服务", "放松"],
        ],
    },
    "innovation": {
        "titles": [
            "{idea}创新工作坊",
            "{idea}创业路演专场",
            "{idea}黑客松冲刺日",
            "{idea}产品设计工作坊",
            "{idea}实验室开放日",
            "{idea}技术分享会",
            "{idea}原型验证营",
            "{idea}跨学科组队沙龙",
            "{idea}竞赛备赛训练",
            "{idea}创客空间开放体验",
        ],
        "topics": [
            "智能硬件", "校园服务 App", "机器人路径规划", "开源项目贡献",
            "数学建模竞赛", "电子设计竞赛", "嵌入式开发", "AIGC 应用创意",
            "可持续材料", "医疗健康产品", "教育科技工具", "社交产品 MVP",
            "无人机航拍", "物联网传感器", "区块链应用探索", "游戏关卡设计",
        ],
        "descs": [
            "围绕{topic}展开动手实践与方案打磨，导师现场点评。欢迎跨专业组队，现场提供部分开发板与工具，请自备笔记本电脑。",
            "聚焦{topic}的创意落地，从问题定义到原型演示。适合想参加竞赛、申请项目或寻找合伙人的同学。",
            "高强度{topic}共创活动，强调快速迭代与展示表达。优秀团队有机会获得孵化资源对接与后续辅导。",
            "开放式{topic}体验与技术分享，包含 demo 展示、问答与自由组队时间。零基础可观摩，有经验者可带项目来。",
        ],
        "tags": [
            ["编程", "AI"], ["创业", "创新"], ["机器人", "编程"], ["AI", "创业"],
            ["创新", "编程"], ["电竞", "编程"], ["数学建模", "编程"],
        ],
    },
}

FIRST_NAMES_TEACHER = [
    "王老师", "李老师", "张老师", "刘老师", "陈老师", "杨老师", "赵老师",
    "黄老师", "周老师", "吴老师", "徐老师", "孙老师", "胡老师", "朱老师",
    "高老师", "林老师", "何老师", "郭老师", "马老师", "罗老师",
]

# Hand-crafted demo activities kept as id 1..N for docs / semantic-search demos
DEMO_ACTIVITIES = [
    {
        "title": "AI 与大模型技术前沿讲座",
        "category": "academic",
        "description": "本次讲座邀请业界专家，介绍大模型在软件工程、教育等领域的最新应用与发展趋势，适合对 AI 感兴趣的同学参加。",
        "start_time": datetime(2026, 7, 15, 14, 0),
        "end_time": datetime(2026, 7, 15, 16, 0),
        "location": "软件大楼 A101",
        "organizer_id": "T001",
        "college": "软件学院",
        "poster": "https://picsum.photos/seed/ai-lecture/800/400",
        "max_participants": 120,
        "signup_count": 85,
        "favorite_count": 42,
        "view_count": 520,
        "check_in_count": 0,
        "hotness_score": 38.15,
        "status": "published",
        "tags": ["AI", "编程"],
        "check_in_code": "AI2026",
    },
    {
        "title": "校园羽毛球友谊赛",
        "category": "sports",
        "description": "面向全校师生的羽毛球双打友谊赛，按学院分组，优胜队伍将获得精美奖品。请提前热身，穿运动服参赛。",
        "start_time": datetime(2026, 7, 20, 9, 0),
        "end_time": datetime(2026, 7, 20, 12, 0),
        "location": "体育馆羽毛球场",
        "organizer_id": "524030910002",
        "college": "计算机学院",
        "poster": "https://picsum.photos/seed/badminton/800/400",
        "max_participants": 64,
        "signup_count": 43,
        "favorite_count": 28,
        "view_count": 310,
        "check_in_count": 0,
        "hotness_score": 28.4,
        "status": "published",
        "tags": ["羽毛球", "体育运动"],
        "check_in_code": "BD2026",
    },
    {
        "title": "摄影社户外采风活动",
        "category": "club",
        "description": "摄影社组织校园及周边人文采风，专业学长带队讲解构图与后期技巧，欢迎零基础同学加入。",
        "start_time": datetime(2026, 7, 18, 8, 0),
        "end_time": datetime(2026, 7, 18, 17, 0),
        "location": "图书馆前广场集合",
        "organizer_id": "524030910001",
        "college": "软件学院",
        "poster": "https://picsum.photos/seed/photo-club/800/400",
        "max_participants": 30,
        "signup_count": 22,
        "favorite_count": 35,
        "view_count": 280,
        "check_in_count": 0,
        "hotness_score": 32.1,
        "status": "published",
        "tags": ["摄影", "艺术"],
        "check_in_code": "PH2026",
    },
    {
        "title": "程序设计竞赛训练营",
        "category": "innovation",
        "description": "为期一周的算法与数据结构强化训练，涵盖动态规划、图论等高频考点，为 ACM/ICPC 及各类编程竞赛做准备。",
        "start_time": datetime(2026, 7, 22, 19, 0),
        "end_time": datetime(2026, 7, 29, 21, 0),
        "location": "计算机楼 302 实验室",
        "organizer_id": "T001",
        "college": "计算机学院",
        "poster": "https://picsum.photos/seed/coding/800/400",
        "max_participants": 50,
        "signup_count": 38,
        "favorite_count": 56,
        "view_count": 640,
        "check_in_count": 0,
        "hotness_score": 45.2,
        "status": "published",
        "tags": ["编程", "AI"],
        "check_in_code": "CP2026",
    },
    {
        "title": "校园志愿者招募 — 社区服务日",
        "category": "volunteer",
        "description": "组织同学前往周边社区开展助老、环境清洁等志愿服务，可计入志愿服务时长，欢迎热心公益的同学报名。",
        "start_time": datetime(2026, 7, 25, 8, 30),
        "end_time": datetime(2026, 7, 25, 16, 0),
        "location": "校门口集合",
        "organizer_id": "524030910001",
        "college": "软件学院",
        "poster": "https://picsum.photos/seed/volunteer/800/400",
        "max_participants": 40,
        "signup_count": 31,
        "favorite_count": 19,
        "view_count": 210,
        "check_in_count": 31,
        "hotness_score": 24.8,
        "status": "ended",
        "tags": ["志愿服务"],
        "check_in_code": "VL2026",
    },
    {
        "title": "校园音乐节 — 夏日之声",
        "category": "arts",
        "description": "各社团及个人歌手同台演出，涵盖流行、摇滚、民谣等多种风格，现场还有互动抽奖环节。",
        "start_time": datetime(2026, 6, 28, 18, 30),
        "end_time": datetime(2026, 6, 28, 21, 30),
        "location": "中心广场",
        "organizer_id": "524030910002",
        "college": "信息学院",
        "poster": "https://picsum.photos/seed/music/800/400",
        "max_participants": 500,
        "signup_count": 412,
        "favorite_count": 198,
        "view_count": 3200,
        "check_in_count": 380,
        "hotness_score": 210.5,
        "status": "ended",
        "tags": ["音乐", "文艺"],
        "check_in_code": "MU2026",
    },
    {
        "title": "三人篮球校园联赛",
        "category": "sports",
        "description": "3v3 街头篮球赛，开赛前有热身教学，适合各类水平球员。强对抗、快节奏，欢迎热爱篮球的同学组队参赛。",
        "start_time": datetime(2026, 7, 21, 15, 0),
        "end_time": datetime(2026, 7, 21, 19, 0),
        "location": "室外篮球场",
        "organizer_id": "524030910002",
        "college": "计算机学院",
        "poster": "https://picsum.photos/seed/basketball/800/400",
        "max_participants": 48,
        "signup_count": 36,
        "favorite_count": 22,
        "view_count": 290,
        "check_in_count": 0,
        "hotness_score": 26.0,
        "status": "published",
        "tags": ["篮球", "体育运动"],
        "check_in_code": "BK2026",
    },
    {
        "title": "量子计算与量子物理入门讲座",
        "category": "academic",
        "description": "从量子叠加与纠缠讲起，介绍量子比特、量子门与近期量子计算实验进展，偏重物理与信息科学交叉，需具备一定数理基础。",
        "start_time": datetime(2026, 7, 16, 19, 0),
        "end_time": datetime(2026, 7, 16, 21, 0),
        "location": "理科楼报告厅",
        "organizer_id": "T001",
        "college": "信息学院",
        "poster": "https://picsum.photos/seed/quantum/800/400",
        "max_participants": 100,
        "signup_count": 47,
        "favorite_count": 18,
        "view_count": 260,
        "check_in_count": 0,
        "hotness_score": 22.3,
        "status": "published",
        "tags": ["物理", "学术"],
        "check_in_code": "QU2026",
    },
    {
        "title": "烘焙社 — 周末手工饼干工作坊",
        "category": "club",
        "description": "亲手完成一盒黄油曲奇，学习称量、搅拌与烤箱温度控制。材料现场提供，适合想轻松解压、体验动手乐趣的同学。",
        "start_time": datetime(2026, 7, 19, 14, 0),
        "end_time": datetime(2026, 7, 19, 17, 0),
        "location": "学生活动中心厨房",
        "organizer_id": "524030910001",
        "college": "软件学院",
        "poster": "https://picsum.photos/seed/baking/800/400",
        "max_participants": 20,
        "signup_count": 18,
        "favorite_count": 27,
        "view_count": 190,
        "check_in_count": 0,
        "hotness_score": 21.6,
        "status": "published",
        "tags": ["烘焙", "休闲"],
        "check_in_code": "BA2026",
    },
    {
        "title": "校际辩论邀请赛选拔",
        "category": "academic",
        "description": "围绕公共议题进行立论、质询与结辩训练，最终选拔代表队参加校际辩论赛。锻炼逻辑表达与临场应变。",
        "start_time": datetime(2026, 7, 17, 18, 30),
        "end_time": datetime(2026, 7, 17, 21, 0),
        "location": "教学楼 B203",
        "organizer_id": "T001",
        "college": "软件学院",
        "poster": "https://picsum.photos/seed/debate/800/400",
        "max_participants": 40,
        "signup_count": 25,
        "favorite_count": 15,
        "view_count": 150,
        "check_in_count": 0,
        "hotness_score": 16.8,
        "status": "published",
        "tags": ["辩论", "表达"],
        "check_in_code": "DB2026",
    },
    {
        "title": "大学生创业路演夜",
        "category": "innovation",
        "description": "创业团队 8 分钟路演 + 导师点评，主题涵盖校园服务、教具硬件与社交媒体产品。寻找合伙人或早期用户的同学欢迎观摩。",
        "start_time": datetime(2026, 7, 23, 18, 0),
        "end_time": datetime(2026, 7, 23, 21, 0),
        "location": "创业孵化中心",
        "organizer_id": "T001",
        "college": "计算机学院",
        "poster": "https://picsum.photos/seed/startup/800/400",
        "max_participants": 80,
        "signup_count": 52,
        "favorite_count": 33,
        "view_count": 410,
        "check_in_count": 0,
        "hotness_score": 34.5,
        "status": "published",
        "tags": ["创业", "创新"],
        "check_in_code": "ST2026",
    },
    {
        "title": "心理健康工作坊 — 压力与放松",
        "category": "club",
        "description": "循证放松练习、正念呼吸与情绪日记分享，帮助缓解备考与实习压力。小班引导，注重隐私与安全感。",
        "start_time": datetime(2026, 7, 24, 16, 0),
        "end_time": datetime(2026, 7, 24, 18, 0),
        "location": "心理中心团体室",
        "organizer_id": "524030910001",
        "college": "软件学院",
        "poster": "https://picsum.photos/seed/mindfulness/800/400",
        "max_participants": 25,
        "signup_count": 19,
        "favorite_count": 41,
        "view_count": 330,
        "check_in_count": 0,
        "hotness_score": 29.7,
        "status": "published",
        "tags": ["心理", "放松"],
        "check_in_code": "MH2026",
    },
    {
        "title": "书法社团钢笔字入门课",
        "category": "arts",
        "description": "临摹楷书基本笔画与结构，适合想把笔记写工整、培养静心习惯的同学。笔墨纸自备或现场借阅。",
        "start_time": datetime(2026, 7, 18, 19, 0),
        "end_time": datetime(2026, 7, 18, 21, 0),
        "location": "文科楼书画室",
        "organizer_id": "524030910002",
        "college": "信息学院",
        "poster": "https://picsum.photos/seed/calligraphy/800/400",
        "max_participants": 28,
        "signup_count": 14,
        "favorite_count": 12,
        "view_count": 120,
        "check_in_count": 0,
        "hotness_score": 11.2,
        "status": "published",
        "tags": ["书法", "文艺"],
        "check_in_code": "CL2026",
    },
    {
        "title": "机器人兴趣小组调试日",
        "category": "innovation",
        "description": "轮式小车传感器校准与路径跟踪 demo，组队完成迷宫闯关。欢迎电子、机械与编程方向混合组队。",
        "start_time": datetime(2026, 7, 26, 9, 0),
        "end_time": datetime(2026, 7, 26, 17, 0),
        "location": "工训中心 B区",
        "organizer_id": "T001",
        "college": "计算机学院",
        "poster": "https://picsum.photos/seed/robot/800/400",
        "max_participants": 36,
        "signup_count": 29,
        "favorite_count": 24,
        "view_count": 270,
        "check_in_count": 0,
        "hotness_score": 23.9,
        "status": "published",
        "tags": ["机器人", "编程"],
        "check_in_code": "RB2026",
    },
    {
        "title": "英语角 — Movie Night 观影畅谈",
        "category": "club",
        "description": "英文电影片段赏析后分组讨论剧情与角色，轻松练习口语。无需口语达人，欢迎想找语伴的同学。",
        "start_time": datetime(2026, 7, 19, 19, 30),
        "end_time": datetime(2026, 7, 19, 21, 30),
        "location": "国际交流中心",
        "organizer_id": "524030910001",
        "college": "软件学院",
        "poster": "https://picsum.photos/seed/english/800/400",
        "max_participants": 35,
        "signup_count": 21,
        "favorite_count": 16,
        "view_count": 180,
        "check_in_count": 0,
        "hotness_score": 15.4,
        "status": "published",
        "tags": ["英语", "交流"],
        "check_in_code": "EN2026",
    },
    {
        "title": "校园趣味马拉松 5 公里",
        "category": "sports",
        "description": "绕校园一圈的欢乐跑，补给站提供能量胶与饮水，完赛有纪念奖牌。重在参与，不设奖金排名。",
        "start_time": datetime(2026, 7, 27, 7, 0),
        "end_time": datetime(2026, 7, 27, 10, 0),
        "location": "体育馆南门起跑",
        "organizer_id": "524030910002",
        "college": "信息学院",
        "poster": "https://picsum.photos/seed/run/800/400",
        "max_participants": 200,
        "signup_count": 96,
        "favorite_count": 55,
        "view_count": 780,
        "check_in_count": 0,
        "hotness_score": 58.6,
        "status": "published",
        "tags": ["跑步", "体育运动"],
        "check_in_code": "RN2026",
    },
    {
        "title": "桌游社周末解压局",
        "category": "club",
        "description": "狼人杀、阿瓦隆与合作类桌游多桌并行，零食饮料自备。周末晚上来换脑子、认识新朋友。",
        "start_time": datetime(2026, 7, 18, 20, 0),
        "end_time": datetime(2026, 7, 19, 0, 0),
        "location": "学生宿舍区活动室",
        "organizer_id": "524030910002",
        "college": "计算机学院",
        "poster": "https://picsum.photos/seed/boardgame/800/400",
        "max_participants": 40,
        "signup_count": 33,
        "favorite_count": 29,
        "view_count": 240,
        "check_in_count": 0,
        "hotness_score": 27.1,
        "status": "published",
        "tags": ["桌游", "休闲"],
        "check_in_code": "BG2026",
    },
    {
        "title": "有机化学实验室开放日",
        "category": "academic",
        "description": "展示基本有机合成与光谱表征过程，参观安全规范与废液处理。对化学、材料感兴趣的同学优先报名。",
        "start_time": datetime(2026, 7, 22, 13, 30),
        "end_time": datetime(2026, 7, 22, 16, 30),
        "location": "化学楼 2 号实验室",
        "organizer_id": "T001",
        "college": "信息学院",
        "poster": "https://picsum.photos/seed/chem/800/400",
        "max_participants": 30,
        "signup_count": 17,
        "favorite_count": 9,
        "view_count": 95,
        "check_in_count": 0,
        "hotness_score": 10.5,
        "status": "published",
        "tags": ["化学", "实验"],
        "check_in_code": "CH2026",
    },
]


def sql_str(s: str) -> str:
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_json(obj) -> str:
    return sql_str(json.dumps(obj, ensure_ascii=False))


def random_name() -> str:
    return RNG.choice(SURNAMES) + "".join(RNG.choice(GIVEN_CHARS) for _ in range(RNG.randint(1, 2)))


def pick_interests() -> list[str]:
    k = RNG.randint(2, 5)
    return RNG.sample(INTERESTS, k)


def pick_available() -> list[str]:
    k = RNG.randint(1, 3)
    return RNG.sample(AVAILABLE_TIMES, k)


def make_users(n_students: int, n_teachers: int) -> list[dict]:
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
    used_ids = {u["id"] for u in users}

    for i in range(n_students):
        year = 522 + (i % 4)  # 522/523/524/525
        # Use 2xxxx suffix to avoid colliding with demo IDs 524030910001/002
        sid = f"{year}0309{20000 + i:05d}"
        assert sid not in used_ids, sid
        used_ids.add(sid)
        users.append(
            {
                "id": sid,
                "name": random_name(),
                "role": "student",
                "college": RNG.choice(COLLEGES),
                "grade": GRADES[i % 4],
                "interests": pick_interests(),
                "available_time": pick_available(),
            }
        )

    for i in range(n_teachers):
        tid = f"T{i + 2:03d}"
        used_ids.add(tid)
        users.append(
            {
                "id": tid,
                "name": FIRST_NAMES_TEACHER[i % len(FIRST_NAMES_TEACHER)]
                if i < len(FIRST_NAMES_TEACHER)
                else random_name() + "老师",
                "role": "teacher",
                "college": RNG.choice(COLLEGES),
                "grade": "教师",
                "interests": pick_interests()[:3],
                "available_time": pick_available(),
            }
        )
    return users


def make_activity(aid: int, organizers: list[str], base_day: datetime) -> dict:
    category = RNG.choice(list(ACTIVITY_TEMPLATES.keys()))
    tmpl = ACTIVITY_TEMPLATES[category]
    topic = RNG.choice(tmpl["topics"])
    title_pat = RNG.choice(tmpl["titles"])
    # sports/arts use sport/art placeholder names in templates
    title = (
        title_pat.replace("{topic}", topic)
        .replace("{sport}", topic)
        .replace("{club}", topic)
        .replace("{art}", topic)
        .replace("{cause}", topic)
        .replace("{idea}", topic)
    )
    # slight variation to reduce exact duplicates
    if RNG.random() < 0.35:
        title = f"{title}（第{RNG.randint(1, 8)}期）"
    elif RNG.random() < 0.2:
        title = f"{RNG.choice(COLLEGES)} · {title}"

    desc = RNG.choice(tmpl["descs"]).format(topic=topic)
    extras = [
        "报名截止前可随时取消；请关注活动群通知。",
        "现场可能拍照记录用于宣传，如有不便请提前告知组织者。",
        "请携带校园卡以便签到核验。",
        "活动开始前 15 分钟开放签到，迟到超过 30 分钟可能无法入场。",
        "建议提前查阅相关资料，便于互动提问。",
    ]
    if RNG.random() < 0.7:
        desc = desc + " " + RNG.choice(extras)

    day_offset = RNG.randint(-90, 60)
    hour = RNG.choice([8, 9, 10, 13, 14, 15, 16, 18, 19, 20])
    start = base_day + timedelta(days=day_offset, hours=hour, minutes=RNG.choice([0, 30]))
    duration_h = RNG.choice([1.5, 2, 2.5, 3, 4, 6, 8])
    end = start + timedelta(hours=duration_h)

    now = datetime(2026, 7, 17, 12, 0, 0)
    if end < now - timedelta(days=1):
        status = RNG.choices(["ended", "published"], weights=[0.85, 0.15])[0]
    elif start > now + timedelta(days=45):
        status = RNG.choices(["published", "draft"], weights=[0.9, 0.1])[0]
    else:
        status = RNG.choices(["published", "draft", "ended"], weights=[0.88, 0.05, 0.07])[0]

    max_p = RNG.choice([20, 25, 30, 36, 40, 48, 50, 64, 80, 100, 120, 200, 300, 500])
    signup = min(max_p, int(max_p * RNG.uniform(0.15, 0.95))) if status != "draft" else RNG.randint(0, 5)
    fav = int(signup * RNG.uniform(0.2, 0.8)) + RNG.randint(0, 20)
    view = signup * RNG.randint(3, 12) + RNG.randint(10, 200)
    check_in = int(signup * RNG.uniform(0.5, 0.95)) if status == "ended" else RNG.randint(0, max(0, signup // 3))
    hotness = round(signup * 0.4 + fav * 0.3 + view * 0.01 + check_in * 0.2 + RNG.uniform(0, 5), 2)

    tags = RNG.choice(tmpl["tags"])
    if RNG.random() < 0.3:
        extra_tag = RNG.choice(INTERESTS)
        if extra_tag not in tags:
            tags = tags + [extra_tag]

    code = f"{category[:2].upper()}{aid:04d}"
    organizer = RNG.choice(organizers)
    college = RNG.choice(COLLEGES)

    return {
        "id": aid,
        "title": title[:200],
        "category": category,
        "description": desc,
        "start_time": start,
        "end_time": end,
        "location": RNG.choice(LOCATIONS),
        "organizer_id": organizer,
        "college": college,
        "poster": f"https://picsum.photos/seed/act{aid}/800/400",
        "max_participants": max_p,
        "signup_count": signup,
        "favorite_count": fav,
        "view_count": view,
        "check_in_count": check_in,
        "hotness_score": hotness,
        "status": status,
        "tags": tags,
        "check_in_code": code,
    }


FEEDBACK_COMMENTS = [
    "活动组织得很有序，收获很大，希望以后还能参加类似活动。",
    "内容充实，嘉宾讲解清晰，互动环节也很好。",
    "整体不错，建议下次把场地选大一点，人比较多。",
    "氛围很好，认识了不少新朋友，推荐给同学。",
    "时间安排合理，材料准备充分，非常满意。",
    "学到了实用技巧，就是签到流程可以再简化一些。",
    "五星好评！志愿时长认定也很及时。",
    "节目很精彩，音响效果不错，期待下一场。",
    "适合零基础，老师很有耐心，下次还来。",
    "比赛公平激烈，裁判专业，完赛奖牌很有纪念意义。",
    "讲座干货多，PPT 清晰，提问环节答疑到位。",
    "桌游局很开心，零食也准备了，解压必备。",
    "采风路线规划合理，学长讲解构图很有帮助。",
    "创业路演点评犀利又中肯，对项目改进很有启发。",
    "心理工作坊让人放松，小班氛围有安全感。",
    "训练强度适中，教练纠正动作很细致。",
    "志愿服务意义强，居民反馈也好，值得参加。",
    "黑客松节奏紧凑，跨专业组队体验很棒。",
    "书法课安静治愈，笔记确实变工整了。",
    "电影讨论很有深度，口语练习机会多。",
]


RECORD_SUMMARIES = [
    "本次活动顺利结束，共有 {n} 人到场参与，现场反响热烈。组织者已完成物资回收与场地清理，感谢各位志愿者与工作人员。",
    "活动圆满落幕。参与同学累计签到 {n} 人次，产出多组优秀作品/案例，相关照片与回访已整理归档。",
    "本场共服务/接待约 {n} 人，流程执行顺畅，安全无事故。后续将根据问卷反馈优化下一期安排。",
    "活动达到预期目标，到场率良好（约 {n} 人）。已收集参与者建议，拟在下学期继续举办进阶场次。",
]


def fmt_dt(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%d %H:%M:%S.000")


def write_seed(
    path: Path,
    n_students: int = 800,
    n_teachers: int = 40,
    n_activities: int = 1500,
    n_registrations: int = 5500,
    n_favorites: int = 2200,
    n_feedback: int = 1200,
) -> dict:
    users = make_users(n_students, n_teachers)
    student_ids = [u["id"] for u in users if u["role"] == "student"]
    teacher_ids = [u["id"] for u in users if u["role"] == "teacher"]
    organizers = student_ids[:200] + teacher_ids
    participant_pool = [u["id"] for u in users if u["role"] != "admin"]

    base_day = datetime(2026, 7, 17, 0, 0, 0)
    activities = []
    for i, demo in enumerate(DEMO_ACTIVITIES):
        row = dict(demo)
        row["id"] = i + 1
        activities.append(row)
    next_id = len(activities) + 1
    while len(activities) < n_activities:
        activities.append(make_activity(next_id, organizers, base_day))
        next_id += 1

    # Registrations: unique (activity_id, user_id)
    reg_pairs: set[tuple[int, str]] = set()
    registrations = []
    # Prefer denser registrations on popular published activities
    published = [a for a in activities if a["status"] in ("published", "ended")]
    rid = 1
    attempts = 0
    while len(registrations) < n_registrations and attempts < n_registrations * 20:
        attempts += 1
        act = RNG.choice(published if published else activities)
        uid = RNG.choice(participant_pool)
        key = (act["id"], uid)
        if key in reg_pairs or uid == act["organizer_id"]:
            continue
        reg_pairs.add(key)
        if act["status"] == "ended":
            status = RNG.choices(["approved", "rejected", "pending"], weights=[0.85, 0.08, 0.07])[0]
        else:
            status = RNG.choices(["approved", "pending", "rejected"], weights=[0.7, 0.22, 0.08])[0]
        created = act["start_time"] - timedelta(days=RNG.randint(1, 20), hours=RNG.randint(0, 23))
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

    # Sync signup_count roughly with approved regs (soft — keep generated counts mostly)
    approved_by_act: dict[int, int] = {}
    for r in registrations:
        if r["status"] == "approved":
            approved_by_act[r["activity_id"]] = approved_by_act.get(r["activity_id"], 0) + 1
    for a in activities:
        if a["id"] in approved_by_act:
            a["signup_count"] = max(a["signup_count"], approved_by_act[a["id"]])
            a["signup_count"] = min(a["signup_count"], a["max_participants"])

    fav_pairs: set[tuple[str, int]] = set()
    favorites = []
    attempts = 0
    while len(favorites) < n_favorites and attempts < n_favorites * 20:
        attempts += 1
        uid = RNG.choice(participant_pool)
        act = RNG.choice(published if published else activities)
        key = (uid, act["id"])
        if key in fav_pairs:
            continue
        fav_pairs.add(key)
        favorites.append({"user_id": uid, "activity_id": act["id"]})

    for a in activities:
        cnt = sum(1 for f in favorites if f["activity_id"] == a["id"])
        if cnt:
            a["favorite_count"] = max(a["favorite_count"], cnt)

    ended = [a for a in activities if a["status"] == "ended"]
    # About 60% of ended activities get a record
    record_acts = RNG.sample(ended, k=min(len(ended), max(1, int(len(ended) * 0.6))))
    records = []
    for a in record_acts:
        n = max(a["check_in_count"], RNG.randint(5, 40))
        records.append(
            {
                "activity_id": a["id"],
                "summary": RNG.choice(RECORD_SUMMARIES).format(n=n),
                "photos": [
                    f"https://picsum.photos/seed/rec{a['id']}a/400/300",
                    f"https://picsum.photos/seed/rec{a['id']}b/400/300",
                ]
                + (
                    [f"https://picsum.photos/seed/rec{a['id']}c/400/300"]
                    if RNG.random() < 0.4
                    else []
                ),
                "published_at": a["end_time"] + timedelta(hours=RNG.randint(2, 48)),
            }
        )

    # Feedback: prefer users who registered approved on ended/published
    approved_map: dict[int, list[str]] = {}
    for r in registrations:
        if r["status"] == "approved":
            approved_map.setdefault(r["activity_id"], []).append(r["user_id"])

    feedback = []
    fb_pairs: set[tuple[int, str]] = set()
    fb_id = 1
    feedback_targets = [a for a in activities if a["status"] in ("ended", "published") and a["id"] in approved_map]
    attempts = 0
    while len(feedback) < n_feedback and attempts < n_feedback * 30:
        attempts += 1
        if not feedback_targets:
            break
        act = RNG.choice(feedback_targets)
        candidates = approved_map.get(act["id"], [])
        if not candidates:
            continue
        uid = RNG.choice(candidates)
        key = (act["id"], uid)
        if key in fb_pairs:
            continue
        fb_pairs.add(key)
        rating = RNG.choices([5, 4, 3, 2, 1], weights=[0.45, 0.35, 0.12, 0.05, 0.03])[0]
        feedback.append(
            {
                "id": fb_id,
                "activity_id": act["id"],
                "user_id": uid,
                "rating": rating,
                "content": RNG.choice(FEEDBACK_COMMENTS),
                "created_at": act["end_time"] + timedelta(hours=RNG.randint(1, 72))
                if act["status"] == "ended"
                else act["start_time"] + timedelta(hours=RNG.randint(1, 5)),
            }
        )
        fb_id += 1

    # Ensure demo user has some meaningful data
    demo = "524030910001"
    demo_acts = [a["id"] for a in activities[:30] if a["status"] != "draft"]
    for aid in demo_acts[:8]:
        if (aid, demo) not in reg_pairs and activities[aid - 1]["organizer_id"] != demo:
            registrations.append(
                {
                    "id": rid,
                    "activity_id": aid,
                    "user_id": demo,
                    "status": "approved",
                    "created_at": datetime(2026, 7, 1, 10, 0, 0),
                }
            )
            reg_pairs.add((aid, demo))
            rid += 1
        if (demo, aid) not in fav_pairs:
            favorites.append({"user_id": demo, "activity_id": aid})
            fav_pairs.add((demo, aid))

    lines: list[str] = []
    lines.append("SET NAMES utf8mb4;")
    lines.append("")
    lines.append("USE campus_activity;")
    lines.append("")
    lines.append("-- Auto-generated by generate_seed.py for ~10k-scale testing")
    lines.append("-- Password for all demo users: 123456")
    lines.append("-- BCrypt hash generated with cost factor 10")
    lines.append("")

    def batch_insert(header: str, value_rows: list[str], batch_size: int = 200) -> None:
        for i in range(0, len(value_rows), batch_size):
            batch = value_rows[i : i + batch_size]
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

    act_rows = []
    for a in activities:
        act_rows.append(
            f"({a['id']}, {sql_str(a['title'])}, {sql_str(a['category'])}, {sql_str(a['description'])}, "
            f"{sql_str(fmt_dt(a['start_time']))}, {sql_str(fmt_dt(a['end_time']))}, "
            f"{sql_str(a['location'])}, {sql_str(a['organizer_id'])}, {sql_str(a['college'])}, "
            f"{sql_str(a['poster'])}, {a['max_participants']}, {a['signup_count']}, {a['favorite_count']}, "
            f"{a['view_count']}, {a['check_in_count']}, {a['hotness_score']}, {sql_str(a['status'])}, "
            f"{sql_json(a['tags'])}, {sql_str(a['check_in_code'])})"
        )
    batch_insert(
        "INSERT INTO activity (id, title, category, description, start_time, end_time, location, "
        "organizer_id, college, poster, max_participants, signup_count, favorite_count, "
        "view_count, check_in_count, hotness_score, status, tags, check_in_code) VALUES",
        act_rows,
        batch_size=100,
    )

    reg_rows = [
        f"({r['id']}, {r['activity_id']}, {sql_str(r['user_id'])}, {sql_str(r['status'])}, "
        f"{sql_str(fmt_dt(r['created_at']))})"
        for r in registrations
    ]
    batch_insert(
        "INSERT INTO registration (id, activity_id, user_id, status, created_at) VALUES",
        reg_rows,
    )

    fav_rows = [f"({sql_str(f['user_id'])}, {f['activity_id']})" for f in favorites]
    batch_insert("INSERT INTO favorite (user_id, activity_id) VALUES", fav_rows)

    rec_rows = [
        f"({r['activity_id']}, {sql_str(r['summary'])}, {sql_json(r['photos'])}, "
        f"{sql_str(fmt_dt(r['published_at']))})"
        for r in records
    ]
    if rec_rows:
        batch_insert(
            "INSERT INTO activity_record (activity_id, summary, photos, published_at) VALUES",
            rec_rows,
            batch_size=100,
        )

    fb_rows = [
        f"({f['id']}, {f['activity_id']}, {sql_str(f['user_id'])}, {f['rating']}, "
        f"{sql_str(f['content'])}, {sql_str(fmt_dt(f['created_at']))})"
        for f in feedback
    ]
    if fb_rows:
        batch_insert(
            "INSERT INTO feedback (id, activity_id, user_id, rating, content, created_at) VALUES",
            fb_rows,
        )

    # Reset AUTO_INCREMENT for convenience
    lines.append(f"ALTER TABLE activity AUTO_INCREMENT = {n_activities + 1};")
    lines.append(f"ALTER TABLE registration AUTO_INCREMENT = {len(registrations) + 1};")
    lines.append(f"ALTER TABLE feedback AUTO_INCREMENT = {len(feedback) + 1};")
    lines.append("")

    path.write_text("\n".join(lines), encoding="utf-8")

    stats = {
        "users": len(users),
        "activities": len(activities),
        "registrations": len(registrations),
        "favorites": len(favorites),
        "activity_records": len(records),
        "feedback": len(feedback),
        "total": len(users)
        + len(activities)
        + len(registrations)
        + len(favorites)
        + len(records)
        + len(feedback),
    }
    return stats


def main() -> None:
    out = Path(__file__).resolve().parent / "seed.sql"
    stats = write_seed(out)
    print(f"Wrote {out}")
    for k, v in stats.items():
        print(f"  {k}: {v}")


if __name__ == "__main__":
    main()
