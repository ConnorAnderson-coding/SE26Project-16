## 热度更新功能的实现：

*   **更新 `Activity` 实体**：
    *   新增 `hotness` 字段，用于存储活动的动态热度值。
    *   新增 `viewCount` 字段，用于记录活动的浏览量。
    *   新增 `createdAt` 字段，用于记录活动创建时间，并自动在创建时设置。
    *   新增 `lastModifiedAt` 字段，并配置 JPA Auditing 自动更新此字段，用于追踪活动最近的修改时间。
*   **更新 `ActivityRepository`**：
    *   由于 `ActivityRepository` 继承自 `JpaRepository`，新的 `hotness`、`viewCount`、`createdAt` 和 `lastModifiedAt` 字段已自动支持持久化。
    *   新增了 `findActivitiesToRecalculateHotness` 自定义查询方法，以优化定时任务中需要重新计算热度的活动获取逻辑。
*   **实现 `HotnessCalculationService`**：
    *   创建了 `HotnessCalculationService`，实现了热度值的核心计算逻辑，包括：
        *   基于浏览量、报名数、签到数、收藏数的加权计算。
        *   类 Hacker News 的时间衰减机制。
        *   根据活动状态（未开始、进行中、已结束）的热度加成或衰减。
*   **增强 `CheckInRepository`**：
    *   为 `CheckInRepository` 添加了 `countByActivityId` 方法，以便 `HotnessCalculationService` 能高效获取活动的签到数。
*   **集成 Redis**：
    *   在 `pom.xml` 中添加了 `spring-boot-starter-data-redis` 依赖。
    *   在 `application.yml` 中配置了 Redis 连接信息。
    *   创建了 `RedisService`，用于管理 Redis 中的 `hot:rank` 有序集合，支持热度排行榜的存储和查询。
*   **实现定时热度更新任务**：
    *   创建了 `HotnessUpdateScheduler`，使用 `@Scheduled` 注解每小时执行一次热度更新。
    *   该任务会根据活动创建时间、最后修改时间及状态，筛选出需要重新计算热度的活动，并调用 `HotnessCalculationService` 进行计算。
    *   更新后的热度值会批量写入 MySQL，并同步更新 Redis 的 `hot:rank` 有序集合。
    *   在 `CampusActivityApplication` 中添加了 `@EnableScheduling` 启用定时任务。
*   **增强 `ActivityController`**：
    *   在 `/api/activities/{id}` 的 `GET` 请求中，增加了 `viewCount` 的自增逻辑。
    *   新增了 `/api/activities/hot` API 端点，用于从 Redis 的 `hot:rank` 有序集合中获取热门活动 ID，然后从 MySQL 联查活动详情并返回。
    *   修改了原有的活动列表排序逻辑，使其在按“hot”排序时使用新的 `hotness` 字段。
*   **更新热度因子相关操作**：
    *   确认了 `SignupController` 和 `FavoriteController` 中的相关操作已正确地对 `signupCount` 和 `favoriteCount` 进行增减并保存到数据库。`viewCount` 的更新已在 `ActivityController` 中处理。

## 设置热度更新时间间隔：

修改 [HotnessUpdateScheduler.java] 文件中 `updateActivityHotness` 方法上的 `@Scheduled` 注解中的 `fixedRate` 的值（单位是毫秒）。

修改后的代码会是这样：

```java
@Scheduled(fixedRate = 5000) // 每5秒执行一次
public void updateActivityHotness() {
    // ... 
}
```

## 使用活动的热度信息

**1. 通过专门的 API 获取热门活动列表：**

访问 `ActivityController` 中新增的 `/api/activities/hot` 端点来直接获取按热度排序的活动列表。这个接口支持分页，可以通过 `page` 和 `size` 参数来控制返回结果。

API 地址为 `/api/activities/hot`。

请求示例：
```
GET /api/activities/hot?page=0&size=10
```

这将返回热度最高的10个活动。

**2. 在活动列表中按热度进行排序：**

现有的活动列表 API (`/api/activities`) 中的排序逻辑已经修改，可以通过 `sort=hot` 参数来按照活动热度进行排序。

API 地址：`/api/activities`

请求示例：
```
GET /api/activities?sort=hot
```

这将返回所有活动，并按热度从高到低进行排序。

**内部热度更新机制：**

热度值会由定时任务 [HotnessUpdateScheduler] 定时自动计算和更新。

每次更新后，活动的最新热度值会持久化到 MySQL 数据库中的 `Activity` 实体，并且会同步更新到 Redis 的 `hot:rank` 有序集合中，以供快速查询。

活动的浏览量、报名数和收藏数等因子，在用户进行相应操作时（例如查看活动详情、报名、收藏活动），也会实时更新到数据库中，这些数据将作为下一次热度计算的输入。