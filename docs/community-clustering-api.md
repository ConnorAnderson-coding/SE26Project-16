# 社区聚类 API 契约

## 1. 契约范围与实现状态

本文同步阶段 3E 异步聚类提交与查询 API 的当前实现，并保留后续公开 API 与内部 Python API 的规划契约。浏览器只调用 Spring Boot 的 `/api/v1` 接口，不直接调用 Python 服务。

### 1.1 已实现的公开端点

| 方法与路径 | 权限 | 当前行为 |
| --- | --- | --- |
| `POST /api/v1/admin/community-clustering/runs` | `ROLE_ADMIN` + CSRF | 原子持久化 `PENDING` 运行与冻结输入后返回 `202 Accepted` |
| `GET /api/v1/admin/community-clustering/runs/{runId}` | `ROLE_ADMIN` | 查询指定聚类运行详情 |
| `GET /api/v1/community-clustering/latest` | `authenticated` | 查询最新成功版本的社区与匿名散点 |
| `GET /api/v1/community-clustering/me` | `authenticated` | 查询当前登录用户在最新成功版本中的归属 |

### 1.2 规划中、尚未实现的公开能力

| 方法或能力 | 当前状态 |
| --- | --- |
| 管理员社区成员分页 | 尚未实现；路径与响应中的个人资料范围尚未最终确定 |

除非章节标题明确标注“规划中”，本文其余公开端点描述均表示当前已实现行为。

## 2. 当前公开 API 通用约定

### 2.1 协议与数据格式

- 公开接口前缀为 `/api/v1`，请求和响应使用 UTF-8 JSON。
- 时间字段使用 ISO 8601 UTC，例如 `2026-07-13T02:30:00Z`。
- `runId`、`communityId` 与 `pointId` 均是不透明 `string`；`pointId` 对应运行内的 `CommunityMember.id`，不等于 `userId`。
- 坐标和距离使用 JSON `number`；坐标是有限数且位于 `[0, 100]`。
- 可空字段以 JSON `null` 表示，不以空字符串代替。
- 运行状态为 `PENDING`、`RUNNING`、`SUCCESS` 或 `FAILED`。

### 2.2 认证、Session 与 CSRF

- 当前实现使用 Spring Security HTTP Session，浏览器会话 Cookie 为 `JSESSIONID`。
- 前端跨源请求需要启用凭据发送，例如 Fetch API 使用 `credentials: "include"`。
- 写请求需要有效 CSRF Token；GET 查询不要求 CSRF Token。聚类 POST 必须携带登录后取得的有效 CSRF Token。
- 管理员端点要求 `ROLE_ADMIN`；`latest` 与 `me` 要求 `authenticated`。
- `latest` 与 `me` 的 `currentUserId` 只来自 `Authentication.getName()`。
- 身份和权限不接受 Body、Query 或 Header 中的 `userId`、`createdBy` 或 `role`；客户端传入这些字段不能改变当前用户或权限。

当前统一的认证与授权错误码如下：

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| `401` | `AUTHENTICATION_REQUIRED` | 受保护端点没有有效登录 Session |
| `403` | `ACCESS_DENIED` | 已认证用户没有所需角色或权限 |
| `403` | `CSRF_TOKEN_INVALID` | 写请求缺少或携带无效 CSRF Token |
| `401` | `INVALID_CREDENTIALS` | 登录凭据无效；这是登录流程错误，不是三个聚类 GET 的业务错误 |

### 2.3 当前聚类端点的统一错误结构

四个已实现聚类端点的所有错误响应统一为：

```json
{
  "code": "ERROR_CODE",
  "message": "安全的中文错误说明",
  "details": {}
}
```

`details` 当前固定为空对象 `{}`，包括 404 响应；404 不回显 `runId`。公开错误不得返回异常 `message`、SQL、原始 JSON、堆栈、Python 地址或其他内部服务信息。存储数据损坏产生的内部 `CORRUPT_STORED_DATA` 不对外暴露，统一映射为 `500 INTERNAL_ERROR`。

## 3. 已实现的公开 API

### 3.1 管理员查询运行详情

#### `GET /api/v1/admin/community-clustering/runs/{runId}`

**权限：** `ROLE_ADMIN`。GET 不要求 CSRF Token。

**请求参数：** 仅有必填 Path 参数 `runId`；不接收请求体。

**成功响应：** `200 OK`。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `runId` | `string` | 否 | 运行 ID |
| `version` | `string` | 否 | 唯一版本 |
| `algorithm` | `string` | 否 | 当前为 `KMEANS` |
| `clusterCount` | `integer` | 否 | K 值 |
| `randomState` | `integer` | 否 | 当前为 42 |
| `status` | `string` | 否 | 运行状态 |
| `sampleCount` | `integer` | 是 | `SUCCESS` 时非空；其他状态依已登记快照而定 |
| `featureSchemaVersion` | `string` | 否 | 特征模式版本 |
| `metrics` | `object` | 是 | 仅 `SUCCESS` 非空 |
| `failure` | `object` | 是 | 仅 `FAILED` 非空 |
| `createdAt` | `string(date-time)` | 否 | 创建时间 |
| `startedAt` | `string(date-time)` | 是 | 开始时间 |
| `finishedAt` | `string(date-time)` | 是 | 结束时间 |
| `createdBy` | `string` | 否 | 服务端登记的触发者标识 |

`metrics` 当前字段为 `inertia` 和长度为 2 的 `pcaExplainedVarianceRatio`。`FAILED` 使用结构化的安全失败摘要：

```json
{
  "failure": {
    "code": "PYTHON_SERVICE_UNAVAILABLE",
    "message": "PYTHON_SERVICE_UNAVAILABLE: 聚类服务不可用"
  }
}
```

公开响应没有原始 `errorMessage` 字段。

状态字段语义：

| 状态 | 字段约束 |
| --- | --- |
| `PENDING` | `metrics=null`、`failure=null`、`startedAt=null`、`finishedAt=null` |
| `RUNNING` | `metrics=null`、`failure=null`、`startedAt` 非空、`finishedAt=null` |
| `SUCCESS` | `metrics` 非空、`failure=null`、`startedAt`/`finishedAt`/`sampleCount` 非空 |
| `FAILED` | `metrics=null`、`failure` 非空、`finishedAt` 非空；`startedAt` 可为 `null` |

**成功响应示例：**

```json
{
  "runId": "run-example-001",
  "version": "cc-20260713-0001",
  "algorithm": "KMEANS",
  "clusterCount": 2,
  "randomState": 42,
  "status": "SUCCESS",
  "sampleCount": 18,
  "featureSchemaVersion": "community-features-v1",
  "metrics": {
    "inertia": 31.48,
    "pcaExplainedVarianceRatio": [0.34, 0.21]
  },
  "failure": null,
  "createdAt": "2026-07-13T02:30:00Z",
  "startedAt": "2026-07-13T02:30:01Z",
  "finishedAt": "2026-07-13T02:30:03Z",
  "createdBy": "admin-example"
}
```

**业务错误：**

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `INVALID_RUN_ID` | `runId` 为空、空白或不符合当前长度约束 |
| `404` | `RUN_NOT_FOUND` | 没有对应运行 |
| `500` | `INTERNAL_ERROR` | 存储数据不一致或未预期内部错误 |

认证与授权错误另见 2.2。404 示例不会回显请求中的 `runId`：

```json
{
  "code": "RUN_NOT_FOUND",
  "message": "未找到指定的聚类任务",
  "details": {}
}
```

### 3.2 查询最新成功版本

#### `GET /api/v1/community-clustering/latest`

**权限：** `authenticated`。GET 不要求 CSRF Token；无 Path、Query 或 Body 参数。当前用户只由 `Authentication.getName()` 确定。

**成功响应：** `200 OK`。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `run` | `object` | 否 | 最新成功运行摘要 |
| `run.runId` | `string` | 否 | 运行 ID |
| `run.version` | `string` | 否 | 版本 |
| `run.algorithm` | `string` | 否 | `KMEANS` |
| `run.clusterCount` | `integer` | 否 | K 值 |
| `run.sampleCount` | `integer` | 否 | 样本数 |
| `run.finishedAt` | `string(date-time)` | 否 | 成功完成时间 |
| `communities` | `array<object>` | 否 | 按 `clusterNo` 升序的社区列表 |

`communities[]` 字段：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `communityId` | `string` | 否 | 社区 ID |
| `clusterNo` | `integer` | 否 | 运行内簇编号 |
| `name` | `string` | 否 | 社区名称 |
| `description` | `string` | 是 | 社区描述 |
| `memberCount` | `integer` | 否 | 成员数 |
| `topInterests` | `array<string>` | 否 | 代表性兴趣；无值为 `[]` |
| `color` | `string` | 否 | 展示颜色 |
| `points` | `array<object>` | 否 | 匿名散点 |

`points[]` 只包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pointId` | `string` | 不透明点标识，不是用户 ID |
| `x` | `number` | `[0, 100]` |
| `y` | `number` | `[0, 100]` |
| `currentUser` | `boolean` | 是否属于当前登录用户 |

**成功响应示例：**

```json
{
  "run": {
    "runId": "run-example-001",
    "version": "cc-20260713-0001",
    "algorithm": "KMEANS",
    "clusterCount": 2,
    "sampleCount": 3,
    "finishedAt": "2026-07-13T02:30:03Z"
  },
  "communities": [
    {
      "communityId": "community-example-0",
      "clusterNo": 0,
      "name": "社区 1",
      "description": "主要兴趣：AI、编程",
      "memberCount": 2,
      "topInterests": ["AI", "编程"],
      "color": "#1677FF",
      "points": [
        {"pointId": "point-example-01", "x": 18.2, "y": 74.6, "currentUser": true},
        {"pointId": "point-example-02", "x": 29.4, "y": 81.0, "currentUser": false}
      ]
    },
    {
      "communityId": "community-example-1",
      "clusterNo": 1,
      "name": "社区 2",
      "description": "主要兴趣：羽毛球",
      "memberCount": 1,
      "topInterests": ["羽毛球"],
      "color": "#52C41A",
      "points": [
        {"pointId": "point-example-03", "x": 91.0, "y": 12.5, "currentUser": false}
      ]
    }
  ]
}
```

该端点明确不返回 `userId`、用户姓名、学院、年级、`distanceToCenter`、`assignedAt`、`createdBy`、`metrics` 或 `failure`。普通用户不能通过匿名点关联其他用户资料。

没有成功运行时返回 `404 NO_SUCCESSFUL_RUN`；存储数据不一致或未预期错误返回 `500 INTERNAL_ERROR`。认证错误另见 2.2。

```json
{
  "code": "NO_SUCCESSFUL_RUN",
  "message": "当前还没有可用的社区聚类结果",
  "details": {}
}
```

### 3.3 查询当前用户所属社区

#### `GET /api/v1/community-clustering/me`

**权限：** `authenticated`。GET 不要求 CSRF Token；无 Path、Query 或 Body 参数。该端点不接受 `userId`，当前用户只由 `Authentication.getName()` 确定。

**成功响应：** `200 OK`。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `runId` | `string` | 否 | 最新成功运行 ID |
| `version` | `string` | 否 | 最新成功版本 |
| `membership` | `object` | 是 | 当前用户不在该版本样本中时为 `null` |
| `membership.communityId` | `string` | 否 | 所属社区 ID |
| `membership.clusterNo` | `integer` | 否 | 簇编号 |
| `membership.communityName` | `string` | 否 | 社区名称 |
| `membership.color` | `string` | 否 | 展示颜色 |
| `membership.pointId` | `string` | 否 | 当前用户在该运行内的不透明点标识 |
| `membership.x` | `number` | 否 | `[0, 100]` |
| `membership.y` | `number` | 否 | `[0, 100]` |
| `membership.distanceToCenter` | `number` | 否 | 非负有限数 |

**有归属的响应示例：**

```json
{
  "runId": "run-example-001",
  "version": "cc-20260713-0001",
  "membership": {
    "communityId": "community-example-0",
    "clusterNo": 0,
    "communityName": "社区 1",
    "color": "#1677FF",
    "pointId": "point-example-01",
    "x": 18.2,
    "y": 74.6,
    "distanceToCenter": 0.83
  }
}
```

当前用户不在最新成功运行的样本中时仍返回 `200 OK`：

```json
{
  "runId": "run-example-001",
  "version": "cc-20260713-0001",
  "membership": null
}
```

该端点不返回用户资料或其他成员信息。没有成功运行时返回 `404 NO_SUCCESSFUL_RUN`；存储数据不一致或未预期错误返回 `500 INTERNAL_ERROR`。认证错误另见 2.2。

## 4. Python 功能开关对公开 API 的影响

当配置为：

```properties
community-clustering.python.enabled=false
```

会关闭 Python 客户端、异步提交实现、后台调度器和工作执行器。POST 路由本身始终存在，但返回 `503 CLUSTERING_SERVICE_UNAVAILABLE`，且不会聚合特征、创建 run 或保存输入快照。三个 GET 查询端点、`CommunityClusteringQueryService` 和聚类 Repository 仍然可用，可继续读取历史 `SUCCESS` 或 `FAILED` 运行。

## 5. 管理员提交 API 与规划能力

### 5.1 管理员触发聚类（已实现）

#### `POST /api/v1/admin/community-clustering/runs`

**权限：** `ROLE_ADMIN`，并要求有效 Session 与 CSRF Token。`createdBy` 只取自 `Authentication.getName()`。

请求体可省略、为 `{}`，或只包含可空整数 `clusterCount`；省略或 `null` 时默认 2。端点局部严格 JSON 解析会拒绝未知字段、重复键、尾随 JSON、非整数值，以及 `createdBy`、`role`、`userId` 等身份注入字段。

```json
{"clusterCount": 2}
```

提交线程先在事务外聚合当前有效用户特征并完成 K 值校验，再在一个短事务中原子写入 `PENDING` run 和该 run 的全部冻结输入快照。事务提交后立即返回 `202 Accepted`，设置 `Location: /api/v1/admin/community-clustering/runs/{runId}`，响应为：

输入快照只保存无损执行当前 Python 请求所需的最小算法特征。当前 `FeatureSample` 契约包含可空的 `college` 和 `grade`，因此它们会随提交时的值冻结在内部 `ClusteringRunInput` 中；它们不是公开用户资料副本，不保存完整 `UserAccount` 实体，也不通过本节 POST 响应、运行详情、`latest`、`me` 或公开错误响应返回。后台执行仅从 `ClusteringRunInput` 恢复这些值，不重新读取实时用户资料，从而保证提交后资料变化或应用重启都不会改变本次 Python 请求。

```json
{
  "runId": "run-example-002",
  "version": "cc-20260721-0002",
  "algorithm": "KMEANS",
  "clusterCount": 2,
  "randomState": 42,
  "status": "PENDING",
  "createdAt": "2026-07-21T04:00:00Z"
}
```

`202` 只表示任务及输入快照已经持久化接受，不表示 Python 已开始或聚类已成功。后台调度器从数据库领取 `PENDING`，只使用已冻结快照构建 Python 请求；客户端应轮询管理员 GET 详情，直至 `SUCCESS` 或 `FAILED`。POST 返回后发生的 Python、协议或持久化失败只反映在运行详情中，不回写原 HTTP 响应。

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `INVALID_CLUSTERING_REQUEST` | JSON 格式、字段或类型非法 |
| `400` | `NO_EFFECTIVE_USERS` | 没有有效用户样本 |
| `400` | `INVALID_CLUSTER_COUNT` | K 小于 2 或大于样本数 |
| `409` | `RUN_CONFLICT` | 已存在 `PENDING` 或 `RUNNING` 活动运行 |
| `503` | `CLUSTERING_SERVICE_UNAVAILABLE` | Python/后台能力被配置关闭 |
| `500` | `INTERNAL_ERROR` | 提交持久化或未预期内部错误 |

任务事实来源是数据库中的 `PENDING` 记录，不是进程内队列。单线程有界执行器只承载当前进程已领取的工作：应用启动时保留 `PENDING` 供后续重新领取；上次进程遗留的 `RUNNING` 固定标记为 `FAILED`（`EXECUTION_INTERRUPTED`）并释放 active slot。当前恢复语义仅支持单应用实例，不提供分布式 lease、心跳、leader election，也不承诺消息队列级 exactly-once。数据库 active slot 保证同时最多一个活动 run。

如果未来从聚类输入中移除 `college` 或 `grade`，必须通过新的 `featureSchemaVersion` 和对应的 Python 契约版本实施，不能在读取旧快照时静默删除、回填或改变字段语义。

### 5.2 管理员社区成员分页

> **尚未实现。当前没有管理员成员分页 Controller 或响应 DTO。**

规划方向是管理员按社区分页查询成员，但路径、分页参数、响应字段和审计要求仍需实现前确认。尤其是是否公开 `userId`、姓名、学院、年级属于待定的隐私与最小权限决策；本文不将这些字段声明为已实现契约，也不提供会被误认为真实响应的成员样例。

## 6. 内部 Python API

### 6.1 执行聚类

#### `POST /internal/v1/clustering/run`

**用途：** 对 Spring Boot 提供的一次不可变特征快照执行预处理、标准化、K-Means 和 PCA，返回未持久化的计算结果。

**调用者：** 仅 Spring Boot 的共享聚类运行执行器。

**权限：** 内部服务身份与网络边界双重保护；不接受浏览器或普通用户身份。具体内部认证机制在实现阶段结合部署环境决定，凭据不得出现在请求业务字段、日志或公开响应中。

**请求参数：** 无 Path 或 Query 参数；Body 如下。

| 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- |
| `runId` | `string` | 是 | Spring Boot 已创建的运行 ID，原样回传 |
| `version` | `string` | 是 | 唯一版本，原样回传 |
| `algorithm` | `string` | 是 | 固定 `KMEANS` |
| `clusterCount` | `integer(int32)` | 是 | `2 <= K <= samples.length` |
| `randomState` | `integer(int32)` | 是 | 必须为 42 |
| `featureSchemaVersion` | `string` | 是 | MVP 固定 `community-features-v1` |
| `samples` | `array<object>` | 是 | 有效用户特征行，至少 2 条，`userId` 唯一；数组顺序不影响逻辑结果 |
| `samples[].userId` | `string` | 是 | 用户关联键；仅用于结果对应，不作为数值特征 |
| `samples[].interests` | `array<string>` | 是 | 兴趣标签，无值为 `[]` |
| `samples[].college` | `string` | 否 | 学院，可为 `null` |
| `samples[].grade` | `string` | 否 | 年级，可为 `null` |
| `samples[].availableTime` | `array<string>` | 是 | 可参与时间，无值为 `[]` |
| `samples[].signupCount` | `integer(int32)` | 是 | 严格整数，范围 `0..2147483647` |
| `samples[].approvedSignupCount` | `integer(int32)` | 是 | 严格整数，范围 `0..2147483647`，且不大于报名次数 |
| `samples[].favoriteCount` | `integer(int32)` | 是 | 严格整数，范围 `0..2147483647` |
| `samples[].checkInCount` | `integer(int32)` | 是 | 严格整数，范围 `0..2147483647` |
| `samples[].feedbackCount` | `integer(int32)` | 是 | 严格整数，范围 `0..2147483647` |
| `samples[].averageRating` | `number(double)` | 否 | 无评价为 `null`；有值时遵循现有评分范围 |
| `samples[].categoryParticipationCounts` | `object<string, integer(int32)>` | 是 | 活动类别到审核通过报名次数；值为严格整数，范围 `0..2147483647` |

请求不得包含密码、令牌、浏览数据、Python 地址或与计算无关的个人信息。
所有计数字段均拒绝布尔值、浮点数、字符串及 int32 范围外整数；此类模型边界错误统一返回 `400 INVALID_SAMPLE_DATA`，不得进入 `500 INTERNAL_ERROR`。Python 服务内部按 `userId` 升序复制并排序样本后执行预处理、K-Means、PCA、兴趣统计和成员结果组装，不要求 Spring Boot 预先排序，也不修改请求对象。

**请求 JSON 示例：**

```json
{
  "runId": "run-example-001",
  "version": "cc-20260713-0001",
  "algorithm": "KMEANS",
  "clusterCount": 2,
  "randomState": 42,
  "featureSchemaVersion": "community-features-v1",
  "samples": [
    {
      "userId": "user-example-01",
      "interests": ["AI", "摄影"],
      "college": "软件学院",
      "grade": "2024级",
      "availableTime": ["weekday_evening", "weekend"],
      "signupCount": 5,
      "approvedSignupCount": 4,
      "favoriteCount": 3,
      "checkInCount": 3,
      "feedbackCount": 2,
      "averageRating": 4.5,
      "categoryParticipationCounts": {"academic": 3, "sports": 1}
    },
    {
      "userId": "user-example-02",
      "interests": ["羽毛球"],
      "college": "计算机学院",
      "grade": "2023级",
      "availableTime": ["weekend"],
      "signupCount": 3,
      "approvedSignupCount": 2,
      "favoriteCount": 1,
      "checkInCount": 2,
      "feedbackCount": 0,
      "averageRating": null,
      "categoryParticipationCounts": {"academic": 0, "sports": 2}
    }
  ]
}
```

**成功响应：** `200 OK`。Python 只返回计算结果，成功状态和持久化由 Spring Boot 决定。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `runId` | `string` | 否 | 原样回传 |
| `version` | `string` | 否 | 原样回传 |
| `algorithm` | `string` | 否 | `KMEANS` |
| `clusterCount` | `integer(int32)` | 否 | K 值 |
| `sampleCount` | `integer(int32)` | 否 | 成员结果数 |
| `metrics` | `object` | 否 | 非敏感计算指标 |
| `metrics.inertia` | `number(double)` | 否 | K-Means inertia，非负有限数 |
| `metrics.pcaExplainedVarianceRatio` | `array<number>` | 否 | 长度固定为 2；每项为 `[0, 1]` 内有限数，零方差主成分返回 `0.0` |
| `communities` | `array<object>` | 否 | 恰好 K 个社区摘要 |
| `communities[].clusterNo` | `integer(int32)` | 否 | 唯一，范围 `0..K-1` |
| `communities[].memberCount` | `integer(int32)` | 否 | 正整数 |
| `communities[].topInterests` | `array<string>` | 否 | 从输入兴趣按频次降序统计，频次相同时按字符串升序；最多 3 项，不得生成虚构标签 |
| `members` | `array<object>` | 否 | 每个输入用户恰好一条，按 `userId` 字符串升序返回 |
| `members[].userId` | `string` | 否 | 输入用户 ID |
| `members[].clusterNo` | `integer(int32)` | 否 | 所属簇 |
| `members[].coordinateX` | `number(double)` | 否 | `[0, 100]` |
| `members[].coordinateY` | `number(double)` | 否 | `[0, 100]` |
| `members[].distanceToCenter` | `number(double)` | 否 | 标准化特征空间中的非负有限距离 |

**成功响应 JSON 示例：**

```json
{
  "runId": "run-example-001",
  "version": "cc-20260713-0001",
  "algorithm": "KMEANS",
  "clusterCount": 2,
  "sampleCount": 2,
  "metrics": {
    "inertia": 0.0,
    "pcaExplainedVarianceRatio": [1.0, 0.0]
  },
  "communities": [
    {"clusterNo": 0, "memberCount": 1, "topInterests": ["AI", "摄影"]},
    {"clusterNo": 1, "memberCount": 1, "topInterests": ["羽毛球"]}
  ],
  "members": [
    {"userId": "user-example-01", "clusterNo": 0, "coordinateX": 0.0, "coordinateY": 50.0, "distanceToCenter": 0.0},
    {"userId": "user-example-02", "clusterNo": 1, "coordinateX": 100.0, "coordinateY": 50.0, "distanceToCenter": 0.0}
  ]
}
```

Python 不生成或返回 `communityId`、`name`、`description`、`color`。Spring Boot 校验 Python 结果后按以下 MVP 固定规则补齐展示元数据并持久化：

- `communityId`：生成新的不透明 `Community.id`。
- `name`：固定为 `社区 {clusterNo + 1}`。
- `description`：`topInterests` 非空时固定为 `主要兴趣：{按顺序以“、”连接的 topInterests}`；为空时为 `null`。
- `color`：按 `clusterNo` 从版本化调色板 `community-display-v1` 循环选择；调色板顺序固定为 `#1677FF`、`#52C41A`、`#FA8C16`、`#EB2F96`、`#722ED1`、`#13C2C2`、`#F5222D`、`#2F54EB`。

#### PCA 退化规则

- 对两个 PCA 投影维度分别归一化。固定使用绝对容差 `PCA_AXIS_ABS_TOLERANCE=1e-12` 和相对容差 `PCA_AXIS_REL_TOLERANCE=1e-9`；令 `scale=max(abs(min), abs(max))`，当 `max-min <= 1e-12 + 1e-9 * scale` 时，该轴视为数值上退化，所有坐标返回 `50.0`。超过该容差时线性映射到 `[0, 100]`。
- 某个主成分解释方差为零时，其解释方差比例返回 `0.0`；总方差为零时指标返回 `[0.0, 0.0]`，不得返回 `NaN` 或无穷大。
- 如果不同特征行数量少于 K，导致 K-Means 无法产生 K 个非空社区，返回 `422 CLUSTERING_COMPUTATION_FAILED`，`details.reason` 为 `INSUFFICIENT_DISTINCT_FEATURE_ROWS`。
- 应用上述退化规则后仍无法得到两个有限 PCA 坐标或有限指标时，返回 `422 CLUSTERING_COMPUTATION_FAILED`，`details.reason` 为 `NON_FINITE_PCA_RESULT`。错误详情不得包含原始特征行、矩阵或内部堆栈。

**错误状态：**

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `INVALID_CLUSTER_COUNT` | K 不满足范围 |
| `400` | `INVALID_SAMPLE_DATA` | 用户重复、计数超出严格非负 int32 范围、字段类型错误、缺少 `randomState` 或样本不足 |
| `409` | `INVALID_FEATURE_SCHEMA` | 不支持请求的特征模式版本 |
| `422` | `CLUSTERING_COMPUTATION_FAILED` | 输入合法，但不同特征行不足以形成 K 个非空簇，或无法按退化规则产生有限且完整的聚类/PCA 结果 |
| `500` | `INTERNAL_ERROR` | 未预期内部错误 |

**错误响应 JSON 示例：**

```json
{
  "code": "INVALID_FEATURE_SCHEMA",
  "message": "不支持的特征模式版本",
  "details": {
    "actual": "community-features-v2",
    "supported": ["community-features-v1"]
  }
}
```

**空数据与 K 越界行为：** `samples` 为空或少于 2 时返回 `400 INVALID_SAMPLE_DATA`；缺少必填的 `randomState` 时返回 `400 INVALID_SAMPLE_DATA`；`clusterCount < 2` 或 `clusterCount > samples.length` 时返回 `400 INVALID_CLUSTER_COUNT`，均不返回空成功结果。空兴趣、空可参与时间和空类别计数是合法字段值；Python 按版本化规则处理，不虚构标签。合法输入发生 PCA 或不同特征行退化时，按上文“PCA 退化规则”处理或返回明确的 422 错误。

### 6.2 内部健康检查

#### `GET /internal/v1/health`

**用途：** 检查 Python 进程和聚类组件是否可响应。健康检查不访问或修改业务数据库。

**调用者：** Spring Boot、部署平台或受控的内部运维探针。

**权限：** 仅内部网络/受控探针可访问；不得通过公开 API 代理给浏览器。

**请求参数：** 无。

**请求 JSON 示例：** GET 请求不发送请求体；契约表示为：

```json
{}
```

**成功响应：** `200 OK`。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `status` | `string` | 否 | 固定 `UP` |
| `service` | `string` | 否 | 固定 `clustering-service` |
| `supportedFeatureSchemas` | `array<string>` | 否 | 支持的特征模式版本 |

**成功响应 JSON 示例：**

```json
{
  "status": "UP",
  "service": "clustering-service",
  "supportedFeatureSchemas": ["community-features-v1"]
}
```

**错误状态：** 进程可响应但依赖初始化失败时返回 `503 SERVICE_UNAVAILABLE`；进程不可达时由调用方识别为连接错误，并在公开边界转换为脱敏错误。

**错误响应 JSON 示例：**

```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "聚类服务尚未就绪",
  "details": {}
}
```

**空数据行为：** 无业务数据也应返回 `200 UP`，因为健康检查不以样本或成功运行是否存在作为健康条件。响应不得包含主机名、端口、数据库信息或凭据。

## 7. Spring Boot 结果校验责任

收到 Python 成功响应后，Spring Boot 在持久化前必须至少校验：

- `runId`、`version`、算法、K 和样本数与请求一致。
- `communities` 恰好包含 K 个互不重复的 `clusterNo`。
- `members` 用户集合与输入集合完全一致，且每个用户只出现一次。
- 每个成员引用存在的 `clusterNo`，各簇成员数与社区摘要一致。
- 持久化后每个社区的 `points` 数量与 `memberCount` 一致，`pointId` 仅来自对应 `CommunityMember.id`。
- 坐标位于 `[0, 100]`，距离和指标为有限数，不接受 `NaN` 或无穷大。
- 返回内容不含协议外字段中的秘密或内部地址；持久化仅使用白名单字段。

任何校验失败都不得写入部分社区结果，应把运行标记为 `FAILED`，同时保留最近一次成功版本。
