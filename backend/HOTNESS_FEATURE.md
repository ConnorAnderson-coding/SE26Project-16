# 活动热度计算与更新功能文档

## 1. 功能概述

此文档旨在向团队成员介绍新上线的活动热度计算与更新功能。该功能为每个活动设计了动态热度值，综合反映了活动的受欢迎程度与时效性。热度值将用于活动列表排序、推荐召回兜底、首页热门展示等多个业务场景。

## 2. 技术实现

### 2.1 热度计算逻辑

活动热度值由以下四个维度加权计算而成：

-   **浏览量 (viewCount)**: 权重 0.15
-   **报名数 (signupCount)**: 权重 0.35
-   **签到数 (checkInCount)**: 权重 0.30
-   **收藏数 (favoriteCount)**: 权重 0.20

**时间衰减机制**：采用类 Hacker News 的衰减算法。以活动发布时间为基准，新活动热度更高，随着时间推移热度逐渐衰减。具体衰减策略为：
-   发布后 0-12 小时：衰减缓慢
-   12-72 小时：逐步下降
-   72 小时后：趋于平稳，但仍保留剩余热度

**活动状态修正**：根据活动当前状态对热度值进行修正，避免已结束活动长期占据榜单：
-   未开始且距开始 > 48 小时：热度 × 0.8
-   未开始且距开始 ≤ 48 小时：热度 × 1.3
-   进行中：热度 × 1.5
-   已结束 ≤ 7 天：热度 × 0.7
-   已结束 > 7 天：热度 × 0.3
-   已取消：热度为 0

### 2.2 定时任务

系统配置了定时任务，**每小时**执行一次全量热度更新。为了提高效率，仅对满足以下条件的活动进行热度重算：
-   发布 7 天以内
-   过去 1 小时内有新动态（例如浏览、报名、签到、收藏等）
-   活动状态为“进行中”

### 2.3 数据存储

-   **MySQL**: 用于持久化存储每个活动的热度值 (`hotnessScore` 字段)。
-   **Redis**: 使用 `ZSET` (有序集合) 存储活动的热度排行榜 (`activity:hotness` 键)，支持高效的按分数倒序查询。

## 3. 如何使用

### 3.1 获取热门活动列表

首页热门推荐或需要快速获取 Top-N 热门活动的场景，可以直接从 Redis 的 `activity:hotness` ZSET 中按分数倒序获取活动 ID。例如，获取 Top 10 活动 ID：

```java
// 示例代码 (RedisTemplate)
Set<String> hotActivityIds = stringRedisTemplate.opsForZSet().reverseRange(ACTIVITY_HOTNESS_ZSET_KEY, 0, 9);
```
获取到活动 ID 后，可根据 ID 批量查询 MySQL 获取活动详情。

### 3.2 通过热度排序获取活动

用户在活动列表页选择按热度排序时，可通过 Redis 的 `REVRANGE` 命令进行分页获取活动 ID，然后联查 MySQL 补全活动详情。

```java
// 示例代码 (RedisTemplate)
long start = (page - 1) * size;
long end = page * size - 1;
Set<String> paginatedActivityIds = stringRedisTemplate.opsForZSet().reverseRange(ACTIVITY_HOTNESS_ZSET_KEY, start, end);
```

### 3.3 推荐冷启动

在推荐系统冷启动或无其他推荐策略可用时，可直接以热度最高的活动作为默认召回结果。

### 3.4 相关代码位置

-   **热度计算与更新服务**: [ActivityHotnessService.java](file:///Users/xuchisheng/2024软工原/backend/src/main/java/com/example/demo/service/ActivityHotnessService.java)
-   **活动实体**: `Activity.java` (新增 `hotnessScore`, `viewCount`, `checkInCount` 字段)

## 4. 配置

热度计算中的各项权重（`VIEW_WEIGHT`, `SIGNUP_WEIGHT`, `CHECKIN_WEIGHT`, `FAVORITE_WEIGHT`）和 Hacker News 衰减算法中的 `GRAVITY` 参数目前在 [ActivityHotnessService.java](file:///Users/xuchisheng/2024软工原/backend/src/main/java/com/example/demo/service/ActivityHotnessService.java) 中定义为常量。未来可考虑将其配置化，以便根据实际运营情况进行灵活调整。

## 5. 验证

您可以通过以下方式验证功能：
1.  **启动 Spring Boot 应用**：确保 MySQL 和 Redis 服务正常运行。
2.  **检查 MySQL 数据库**：查看 `activity` 表中 `hotness_score` 字段是否按预期更新。
3.  **检查 Redis**：使用 Redis 客户端查看 `activity:hotness` ZSET 的内容，验证活动热度排序是否正确。