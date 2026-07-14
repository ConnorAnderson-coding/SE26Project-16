# 社区聚类 API 契约

## 1. 契约范围

本文定义社区聚类迭代 2 的五个 Spring Boot 公开 API 和两个 Python 内部 API。本文是阶段 0 设计，不代表接口已经实现。

## 2. 通用约定

### 2.1 协议与数据格式

- 公开接口前缀为 `/api/v1`，仅由 Spring Boot 对浏览器提供。
- 内部接口前缀为 `/internal/v1`，仅供 Spring Boot 到 Python 服务的服务端调用。
- 请求和响应使用 UTF-8 JSON；健康检查成功响应也使用 JSON。
- 时间字段类型为 `string(date-time)`，采用带时区的 ISO 8601 格式，例如 `2026-07-13T10:30:00+08:00`。
- 标识字段在 JSON 中均为 `string`。`runId` 对应数据模型 `ClusteringRun.id`，`communityId` 对应 `Community.id`，`userId` 原样对应现有 `UserAccount.id`；公开 API 与内部 API 不得对同一标识做数值转换。
- `runId`、`communityId` 和仅用于持久化的 `CommunityMember.id` 由 Spring Boot 生成，均为长度 1 到 64 的不透明字符串；客户端不得解析其内部格式。最新概览中的 `pointId` 对应 `CommunityMember.id`，但绝不等同于 `userId`。
- 坐标和距离使用 JSON `number`；坐标必须为有限数且位于 `[0, 100]`。
- 未特别说明的可空字段返回 JSON `null`，不以空字符串代替。
- 任务状态枚举固定为 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`。

### 2.2 统一错误结构

所有公开及内部接口的非 2xx 响应均使用：

```json
{
  "code": "CLUSTERING_ERROR_CODE",
  "message": "中文错误说明",
  "details": {}
}
```

字段类型：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | `string` | 是 | 稳定、可供程序判断的错误码 |
| `message` | `string` | 是 | 面向调用者的中文错误说明，不包含凭据、内部地址或堆栈 |
| `details` | `object` | 是 | 非敏感结构化上下文；无详情时为 `{}` |

常用错误码包括 `INVALID_CLUSTER_COUNT`、`NO_EFFECTIVE_USERS`、`UNAUTHENTICATED`、`FORBIDDEN`、`RUN_NOT_FOUND`、`COMMUNITY_NOT_FOUND`、`NO_SUCCESSFUL_RUN`、`RUN_CONFLICT`、`INVALID_FEATURE_SCHEMA`、`INVALID_CLUSTERING_RESULT`、`PYTHON_SERVICE_UNAVAILABLE` 和 `INTERNAL_ERROR`。

### 2.3 安全边界

- 管理员权限和当前用户身份必须由 Spring Boot 的可信服务端认证上下文提供，不接受请求参数伪造角色或当前用户 ID。
- 当前仓库没有完整服务端认证体系；本文只定义最终权限语义，不在本阶段设计新的登录系统。
- 公开 API 永不返回用户密码、认证令牌、内部特征向量、标准化矩阵、Python 服务地址或内部调用凭据。
- 最新概览中的散点使用运行内不透明 `pointId`；普通用户不能据此反推出其他用户账号。
- Python 地址只允许存在于 Spring Boot 服务端配置中。

## 3. 公开 API

## 3.1 管理员手动触发聚类

### `POST /api/v1/admin/community-clustering/runs`

**用途：** 创建并异步触发一次社区聚类运行。

**调用者：** 管理后台通过 Spring Boot 调用。

**权限：** 仅管理员。Spring Boot 必须从可信身份上下文判断权限。

**请求参数：**

| 位置 | 字段 | 类型 | 必填 | 约束与说明 |
| --- | --- | --- | --- | --- |
| Body | `clusterCount` | `integer(int32)` | 否 | K 值；省略时本地演示默认 2；必须满足 `2 <= K <= 有效用户数` |

不接受客户端覆盖 `algorithm`、`randomState`、运行状态或服务地址；算法固定为 K-Means，随机种子固定为 42。

**请求 JSON 示例：**

```json
{
  "clusterCount": 2
}
```

**成功响应：** `202 Accepted`。任务已登记为 `PENDING`，不表示聚类已完成。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `runId` | `string` | 否 | 运行 ID |
| `version` | `string` | 否 | 全局唯一结果版本 |
| `algorithm` | `string` | 否 | 固定为 `KMEANS` |
| `clusterCount` | `integer(int32)` | 否 | 实际采用的 K |
| `randomState` | `integer(int32)` | 否 | 固定为 42 |
| `status` | `string(enum)` | 否 | 此处为 `PENDING` |
| `createdAt` | `string(date-time)` | 否 | 创建时间 |

**成功响应 JSON 示例：**

```json
{
  "runId": "7a663bd4-95f1-4ef5-a8e9-84267f4fa301",
  "version": "cc-20260713-0001",
  "algorithm": "KMEANS",
  "clusterCount": 2,
  "randomState": 42,
  "status": "PENDING",
  "createdAt": "2026-07-13T10:30:00+08:00"
}
```

**错误状态：**

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `INVALID_CLUSTER_COUNT` | K 不是整数、K 小于 2 或 K 大于有效用户数 |
| `409` | `RUN_CONFLICT` | MVP 策略不允许并行运行且已有 `PENDING`/`RUNNING` 任务 |
| `401` | `UNAUTHENTICATED` | 缺少可信身份 |
| `403` | `FORBIDDEN` | 当前用户不是管理员 |
| `500` | `INTERNAL_ERROR` | 创建运行记录失败 |

**错误响应 JSON 示例：**

```json
{
  "code": "INVALID_CLUSTER_COUNT",
  "message": "聚类数量必须在 2 和有效用户数 18 之间",
  "details": {
    "min": 2,
    "max": 18,
    "actual": 20
  }
}
```

**空数据行为：** 请求体 `{}` 等同于使用本地默认 `clusterCount=2`。有效用户数少于 2 时返回 `400 NO_EFFECTIVE_USERS`，不创建可执行任务，也不调用 Python。

## 3.2 查询聚类任务状态

### `GET /api/v1/admin/community-clustering/runs/{runId}`

**用途：** 查询指定运行的当前状态、参数、计数、时间和脱敏失败摘要。

**调用者：** 管理后台通过 Spring Boot 调用。

**权限：** 仅管理员。

**请求参数：**

| 位置 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| Path | `runId` | `string` | 是 | 运行 ID |

**请求 JSON 示例：** GET 请求不发送请求体；契约中的空对象表示无 Body 字段。

```json
{}
```

**成功响应：** `200 OK`。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `runId` | `string` | 否 | 运行 ID |
| `version` | `string` | 否 | 唯一版本 |
| `algorithm` | `string` | 否 | `KMEANS` |
| `clusterCount` | `integer(int32)` | 否 | K 值 |
| `randomState` | `integer(int32)` | 否 | 42 |
| `status` | `string(enum)` | 否 | 四种统一状态之一 |
| `sampleCount` | `integer(int32)` | 是 | 聚合完成前可为 `null` |
| `featureSchemaVersion` | `string` | 否 | 特征模式版本 |
| `metrics` | `object` | 是 | 成功后可返回非敏感指标；其他状态可为 `null` |
| `startedAt` | `string(date-time)` | 是 | 尚未开始时为 `null` |
| `finishedAt` | `string(date-time)` | 是 | 未结束时为 `null` |
| `errorMessage` | `string` | 是 | 仅 `FAILED` 返回脱敏摘要，其他状态为 `null` |
| `createdBy` | `string` | 否 | 触发管理员的用户 ID |
| `createdAt` | `string(date-time)` | 否 | 创建时间 |

**成功响应 JSON 示例：**

```json
{
  "runId": "7a663bd4-95f1-4ef5-a8e9-84267f4fa301",
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
  "startedAt": "2026-07-13T10:30:01+08:00",
  "finishedAt": "2026-07-13T10:30:03+08:00",
  "errorMessage": null,
  "createdBy": "admin-001",
  "createdAt": "2026-07-13T10:30:00+08:00"
}
```

**错误状态：** `400 INVALID_RUN_ID`、`401 UNAUTHENTICATED`、`403 FORBIDDEN`、`404 RUN_NOT_FOUND`、`500 INTERNAL_ERROR`。

**错误响应 JSON 示例：**

```json
{
  "code": "RUN_NOT_FOUND",
  "message": "未找到指定的聚类任务",
  "details": {
    "runId": "missing-run"
  }
}
```

**空数据行为：** 运行存在时始终返回运行对象；未开始或未结束的时间及暂不可用的指标按字段定义返回 `null`。运行不存在时返回 404，不返回空对象。

## 3.3 查询最新成功版本的社区概览和二维坐标

### `GET /api/v1/community-clustering/latest`

**用途：** 返回最近一次 `SUCCESS` 运行的社区摘要和匿名二维散点，供社区聚类页面展示。

**调用者：** 已登录用户的浏览器通过 Spring Boot 调用。

**权限：** 已登录用户；管理员也可调用。

**请求参数：** 无 Path、Query 或 Body 参数。

**请求 JSON 示例：** GET 请求不发送请求体；契约表示为：

```json
{}
```

**成功响应：** `200 OK`。

顶层字段：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `run` | `object` | 否 | 最新成功运行摘要 |
| `run.runId` | `string` | 否 | 运行 ID |
| `run.version` | `string` | 否 | 版本 |
| `run.algorithm` | `string` | 否 | `KMEANS` |
| `run.clusterCount` | `integer(int32)` | 否 | K 值 |
| `run.sampleCount` | `integer(int32)` | 否 | 样本数 |
| `run.finishedAt` | `string(date-time)` | 否 | 成功完成时间 |
| `communities` | `array<object>` | 否 | 社区列表，按 `clusterNo` 升序 |

`communities[]` 字段：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `communityId` | `string` | 否 | 社区 ID |
| `clusterNo` | `integer(int32)` | 否 | 运行内簇编号，范围 `0..K-1` |
| `name` | `string` | 否 | 展示名称 |
| `description` | `string` | 是 | 展示描述 |
| `memberCount` | `integer(int32)` | 否 | 成员数 |
| `topInterests` | `array<string>` | 否 | 代表性兴趣，最多 3 项，可为空数组 |
| `color` | `string` | 否 | 展示颜色，建议 `#RRGGBB` |
| `points` | `array<object>` | 否 | 该社区匿名散点 |
| `points[].pointId` | `string` | 否 | 对应运行内 `CommunityMember.id` 的不透明点标识，不等同于用户 ID |
| `points[].x` | `number(double)` | 否 | `[0, 100]` |
| `points[].y` | `number(double)` | 否 | `[0, 100]` |
| `points[].isCurrentUser` | `boolean` | 否 | 是否为当前登录用户的点 |

**成功响应 JSON 示例：**

```json
{
  "run": {
    "runId": "7a663bd4-95f1-4ef5-a8e9-84267f4fa301",
    "version": "cc-20260713-0001",
    "algorithm": "KMEANS",
    "clusterCount": 2,
    "sampleCount": 3,
    "finishedAt": "2026-07-13T10:30:03+08:00"
  },
  "communities": [
    {
      "communityId": "community-0",
      "clusterNo": 0,
      "name": "社区 1",
      "description": "主要兴趣：AI、编程",
      "memberCount": 2,
      "topInterests": ["AI", "编程"],
      "color": "#1677FF",
      "points": [
        {"pointId": "p-01", "x": 18.2, "y": 74.6, "isCurrentUser": true},
        {"pointId": "p-02", "x": 29.4, "y": 81.0, "isCurrentUser": false}
      ]
    },
    {
      "communityId": "community-1",
      "clusterNo": 1,
      "name": "社区 2",
      "description": "主要兴趣：羽毛球",
      "memberCount": 1,
      "topInterests": ["羽毛球"],
      "color": "#52C41A",
      "points": [
        {"pointId": "p-03", "x": 91.0, "y": 12.5, "isCurrentUser": false}
      ]
    }
  ]
}
```

**错误状态：** `401 UNAUTHENTICATED`、`404 NO_SUCCESSFUL_RUN`、`500 INTERNAL_ERROR`。

**错误响应 JSON 示例：**

```json
{
  "code": "NO_SUCCESSFUL_RUN",
  "message": "当前还没有可用的社区聚类结果",
  "details": {}
}
```

**空数据行为：** 没有 `SUCCESS` 运行时返回 404。成功运行按约束至少包含 2 个样本和 K 个非空社区；`topInterests` 没有可用值时返回 `[]`，描述可为 `null`，不得用虚构兴趣补齐。

#### 前端消费约定

- 社区聚类页面以 `communities` 直接作为表格、图例和散点图的数据源，不需要再调用管理员成员接口或按 `userId` 关联用户列表。
- `communityId` 用作社区稳定渲染键，表格成员数使用 `memberCount`；`points.length` 必须等于该社区的 `memberCount`。
- 散点以 `pointId` 作为渲染键，直接使用 `x`、`y` 作为百分比坐标，并用 `isCurrentUser` 高亮当前用户。
- 普通用户的散点提示只能显示“当前用户”或“匿名成员”及社区名称，不得沿用当前 mock 页面通过 `userId` 展示其他成员姓名、学院或兴趣的逻辑。
- 因隐私边界不同，阶段 4 必须把当前 mock 的 `id/members/userId` 字段读取改为本契约的 `communityId/points/pointId`；完成这一次字段接入后，`latest` 单个响应应足以渲染公开社区页面。

## 3.4 查询当前用户所属社区

### `GET /api/v1/community-clustering/me`

**用途：** 查询可信身份上下文中的当前用户在最新成功版本中的唯一社区归属。

**调用者：** 已登录用户的浏览器通过 Spring Boot 调用。

**权限：** 已登录用户；不接受 `userId` 参数。

**请求参数：** 无 Path、Query 或 Body 参数，当前用户 ID 从服务端认证上下文取得。

**请求 JSON 示例：**

```json
{}
```

**成功响应：** `200 OK`。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `runId` | `string` | 否 | 最新成功运行 ID |
| `version` | `string` | 否 | 最新成功版本 |
| `membership` | `object` | 是 | 当前用户未纳入该版本时为 `null` |
| `membership.communityId` | `string` | 否 | 所属社区 ID |
| `membership.clusterNo` | `integer(int32)` | 否 | 簇编号 |
| `membership.name` | `string` | 否 | 社区名称 |
| `membership.description` | `string` | 是 | 社区描述 |
| `membership.color` | `string` | 否 | 展示颜色 |
| `membership.coordinateX` | `number(double)` | 否 | `[0, 100]` |
| `membership.coordinateY` | `number(double)` | 否 | `[0, 100]` |
| `membership.distanceToCenter` | `number(double)` | 否 | 非负且有限 |
| `membership.assignedAt` | `string(date-time)` | 否 | 归属保存时间 |

**成功响应 JSON 示例：**

```json
{
  "runId": "7a663bd4-95f1-4ef5-a8e9-84267f4fa301",
  "version": "cc-20260713-0001",
  "membership": {
    "communityId": "community-0",
    "clusterNo": 0,
    "name": "社区 1",
    "description": "主要兴趣：AI、编程",
    "color": "#1677FF",
    "coordinateX": 18.2,
    "coordinateY": 74.6,
    "distanceToCenter": 0.83,
    "assignedAt": "2026-07-13T10:30:03+08:00"
  }
}
```

**错误状态：** `401 UNAUTHENTICATED`、`404 NO_SUCCESSFUL_RUN`、`500 INTERNAL_ERROR`。

**错误响应 JSON 示例：**

```json
{
  "code": "UNAUTHENTICATED",
  "message": "请先登录后再查询所属社区",
  "details": {}
}
```

**空数据行为：** 有最新成功运行但当前用户不在该运行样本中时返回 `200`，`membership` 为 `null`；没有成功运行时返回 `404 NO_SUCCESSFUL_RUN`。不得把当前用户自动分配到最近社区。

## 3.5 管理员查询某社区成员

### `GET /api/v1/admin/community-clustering/communities/{communityId}/members`

**用途：** 分页查询指定社区的成员和其在该运行中的展示信息。

**调用者：** 管理后台通过 Spring Boot 调用。

**权限：** 仅管理员。

**请求参数：**

| 位置 | 字段 | 类型 | 必填 | 默认值/约束 |
| --- | --- | --- | --- | --- |
| Path | `communityId` | `string` | 是 | 社区 ID |
| Query | `page` | `integer(int32)` | 否 | 默认 0，最小 0 |
| Query | `size` | `integer(int32)` | 否 | 默认 20，范围 1 到 100 |

**请求 JSON 示例：** `GET .../members?page=0&size=20` 不发送请求体；契约表示为：

```json
{}
```

**成功响应：** `200 OK`。

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `community` | `object` | 否 | 社区摘要 |
| `community.communityId` | `string` | 否 | 社区 ID |
| `community.runId` | `string` | 否 | 所属运行 ID |
| `community.version` | `string` | 否 | 所属运行版本 |
| `community.clusterNo` | `integer(int32)` | 否 | 簇编号 |
| `community.name` | `string` | 否 | 社区名称 |
| `content` | `array<object>` | 否 | 当前页成员 |
| `content[].userId` | `string` | 否 | 用户 ID，仅管理员可见 |
| `content[].name` | `string` | 否 | 用户展示名 |
| `content[].college` | `string` | 是 | 学院 |
| `content[].grade` | `string` | 是 | 年级 |
| `content[].coordinateX` | `number(double)` | 否 | `[0, 100]` |
| `content[].coordinateY` | `number(double)` | 否 | `[0, 100]` |
| `content[].distanceToCenter` | `number(double)` | 否 | 非负且有限 |
| `content[].assignedAt` | `string(date-time)` | 否 | 归属保存时间 |
| `page` | `integer(int32)` | 否 | 当前页，从 0 开始 |
| `size` | `integer(int32)` | 否 | 页大小 |
| `totalElements` | `integer(int64)` | 否 | 总成员数 |
| `totalPages` | `integer(int32)` | 否 | 总页数 |

**成功响应 JSON 示例：**

```json
{
  "community": {
    "communityId": "community-0",
    "runId": "7a663bd4-95f1-4ef5-a8e9-84267f4fa301",
    "version": "cc-20260713-0001",
    "clusterNo": 0,
    "name": "社区 1"
  },
  "content": [
    {
      "userId": "20260001",
      "name": "张三",
      "college": "软件学院",
      "grade": "2024级",
      "coordinateX": 18.2,
      "coordinateY": 74.6,
      "distanceToCenter": 0.83,
      "assignedAt": "2026-07-13T10:30:03+08:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**错误状态：** `400 INVALID_PAGE_REQUEST`、`401 UNAUTHENTICATED`、`403 FORBIDDEN`、`404 COMMUNITY_NOT_FOUND`、`500 INTERNAL_ERROR`。

**错误响应 JSON 示例：**

```json
{
  "code": "COMMUNITY_NOT_FOUND",
  "message": "未找到指定社区",
  "details": {
    "communityId": "missing-community"
  }
}
```

**空数据行为：** 社区存在但请求页没有成员时返回 `200` 和 `content: []`；社区不存在时返回 404。响应不得包含密码、令牌或成员内部特征向量。

## 4. 内部 Python API

## 4.1 执行聚类

### `POST /internal/v1/clustering/run`

**用途：** 对 Spring Boot 提供的一次不可变特征快照执行预处理、标准化、K-Means 和 PCA，返回未持久化的计算结果。

**调用者：** 仅 Spring Boot 聚类编排组件。

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
| `samples` | `array<object>` | 是 | 有效用户特征行，至少 2 条，`userId` 唯一 |
| `samples[].userId` | `string` | 是 | 用户关联键；仅用于结果对应，不作为数值特征 |
| `samples[].interests` | `array<string>` | 是 | 兴趣标签，无值为 `[]` |
| `samples[].college` | `string` | 否 | 学院，可为 `null` |
| `samples[].grade` | `string` | 否 | 年级，可为 `null` |
| `samples[].availableTime` | `array<string>` | 是 | 可参与时间，无值为 `[]` |
| `samples[].signupCount` | `integer(int32)` | 是 | 非负 |
| `samples[].approvedSignupCount` | `integer(int32)` | 是 | 非负且不大于报名次数 |
| `samples[].favoriteCount` | `integer(int32)` | 是 | 非负 |
| `samples[].checkInCount` | `integer(int32)` | 是 | 非负 |
| `samples[].feedbackCount` | `integer(int32)` | 是 | 非负 |
| `samples[].averageRating` | `number(double)` | 否 | 无评价为 `null`；有值时遵循现有评分范围 |
| `samples[].categoryParticipationCounts` | `object<string, integer>` | 是 | 活动类别到审核通过报名次数，值非负 |

请求不得包含密码、令牌、浏览数据、Python 地址或与计算无关的个人信息。

**请求 JSON 示例：**

```json
{
  "runId": "7a663bd4-95f1-4ef5-a8e9-84267f4fa301",
  "version": "cc-20260713-0001",
  "algorithm": "KMEANS",
  "clusterCount": 2,
  "randomState": 42,
  "featureSchemaVersion": "community-features-v1",
  "samples": [
    {
      "userId": "20260001",
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
      "userId": "20260002",
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
| `members` | `array<object>` | 否 | 每个输入用户恰好一条 |
| `members[].userId` | `string` | 否 | 输入用户 ID |
| `members[].clusterNo` | `integer(int32)` | 否 | 所属簇 |
| `members[].coordinateX` | `number(double)` | 否 | `[0, 100]` |
| `members[].coordinateY` | `number(double)` | 否 | `[0, 100]` |
| `members[].distanceToCenter` | `number(double)` | 否 | 标准化特征空间中的非负有限距离 |

**成功响应 JSON 示例：**

```json
{
  "runId": "7a663bd4-95f1-4ef5-a8e9-84267f4fa301",
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
    {"userId": "20260001", "clusterNo": 0, "coordinateX": 0.0, "coordinateY": 50.0, "distanceToCenter": 0.0},
    {"userId": "20260002", "clusterNo": 1, "coordinateX": 100.0, "coordinateY": 50.0, "distanceToCenter": 0.0}
  ]
}
```

Python 不生成或返回 `communityId`、`name`、`description`、`color`。Spring Boot 校验 Python 结果后按以下 MVP 固定规则补齐展示元数据并持久化：

- `communityId`：生成新的不透明 `Community.id`。
- `name`：固定为 `社区 {clusterNo + 1}`。
- `description`：`topInterests` 非空时固定为 `主要兴趣：{按顺序以“、”连接的 topInterests}`；为空时为 `null`。
- `color`：按 `clusterNo` 从版本化调色板 `community-display-v1` 循环选择；调色板顺序固定为 `#1677FF`、`#52C41A`、`#FA8C16`、`#EB2F96`、`#722ED1`、`#13C2C2`、`#F5222D`、`#2F54EB`。

#### PCA 退化规则

- 对两个 PCA 投影维度分别归一化；若某维最大值大于最小值，线性映射到 `[0, 100]`，否则该维所有坐标返回 `50.0`。
- 某个主成分解释方差为零时，其解释方差比例返回 `0.0`；总方差为零时指标返回 `[0.0, 0.0]`，不得返回 `NaN` 或无穷大。
- 如果不同特征行数量少于 K，导致 K-Means 无法产生 K 个非空社区，返回 `422 CLUSTERING_COMPUTATION_FAILED`，`details.reason` 为 `INSUFFICIENT_DISTINCT_FEATURE_ROWS`。
- 应用上述退化规则后仍无法得到两个有限 PCA 坐标或有限指标时，返回 `422 CLUSTERING_COMPUTATION_FAILED`，`details.reason` 为 `NON_FINITE_PCA_RESULT`。错误详情不得包含原始特征行、矩阵或内部堆栈。

**错误状态：**

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `INVALID_CLUSTER_COUNT` | K 不满足范围 |
| `400` | `INVALID_SAMPLE_DATA` | 用户重复、计数为负、字段类型错误或样本不足 |
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

**空数据与 K 越界行为：** `samples` 为空或少于 2 时返回 `400 INVALID_SAMPLE_DATA`；`clusterCount < 2` 或 `clusterCount > samples.length` 时返回 `400 INVALID_CLUSTER_COUNT`，均不返回空成功结果。空兴趣、空可参与时间和空类别计数是合法字段值；Python 按版本化规则处理，不虚构标签。合法输入发生 PCA 或不同特征行退化时，按上文“PCA 退化规则”处理或返回明确的 422 错误。

## 4.2 内部健康检查

### `GET /internal/v1/health`

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

## 5. Spring Boot 结果校验责任

收到 Python 成功响应后，Spring Boot 在持久化前必须至少校验：

- `runId`、`version`、算法、K 和样本数与请求一致。
- `communities` 恰好包含 K 个互不重复的 `clusterNo`。
- `members` 用户集合与输入集合完全一致，且每个用户只出现一次。
- 每个成员引用存在的 `clusterNo`，各簇成员数与社区摘要一致。
- 持久化后每个社区的 `points` 数量与 `memberCount` 一致，`pointId` 仅来自对应 `CommunityMember.id`。
- 坐标位于 `[0, 100]`，距离和指标为有限数，不接受 `NaN` 或无穷大。
- 返回内容不含协议外字段中的秘密或内部地址；持久化仅使用白名单字段。

任何校验失败都不得写入部分社区结果，应把运行标记为 `FAILED`，同时保留最近一次成功版本。
