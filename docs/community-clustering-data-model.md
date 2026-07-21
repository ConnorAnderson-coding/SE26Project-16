# 社区聚类数据模型

## 1. 设计目标

本模型为迭代 2 的版本化 K-Means 硬聚类结果提供持久化契约。当前实现基线为 H2，后续计划迁移到 MySQL。字段名采用领域模型写法；实际表名和列名可在实现阶段按项目 JPA 命名策略确定，但语义、唯一性和事务约束不得弱化。

核心原则：

- 一次 `ClusteringRun` 固定一份参数、输入口径和结果版本。
- 一个运行包含 K 个 `Community`。
- 一个有效用户在同一运行中恰好有一条 `CommunityMember`，因此只能属于一个社区。
- 只有社区和成员完整提交的 `SUCCESS` 运行才能成为 latest。
- 聚类数据是用户账号的派生数据，删除派生数据绝不能级联删除用户账号。

标识符与 API 字段的固定映射：

- `ClusteringRun.id` 在公开及内部 DTO 中统一命名为 `runId`。
- `Community.id` 在公开 DTO 中统一命名为 `communityId`；Python 只返回 `clusterNo`，不生成社区 ID。
- `CommunityMember.id` 在最新概览的匿名散点中命名为 `pointId`；它不等同于 `userId`，也不能用于反推用户账号。
- `CommunityMember.userId` 原样引用现有 `UserAccount.id`。以上标识在 JSON 和 Java 中均为 `String`，禁止在任一服务边界转换为数值。

## 2. `ClusteringRun`

表示一次聚类任务、其参数快照、执行状态和结果指标。

| 字段 | 建议 Java 类型 | 建议数据库类型 | 可空 | 约束/说明 |
| --- | --- | --- | --- | --- |
| `id` | `String` | `VARCHAR(64)` | 否 | 主键；长度 1 到 64，由 Spring Boot 生成的不透明字符串，对外映射为 `runId` |
| `version` | `String` | `VARCHAR(64)` | 否 | 全局唯一、不可变、面向结果查询的版本标识 |
| `algorithm` | 枚举或 `String` | `VARCHAR(32)` | 否 | MVP 固定 `KMEANS` |
| `clusterCount` | `Integer` | `INT` | 否 | K；必须 `>= 2`，并在创建任务时校验不大于有效用户数 |
| `randomState` | `Integer` | `INT` | 否 | MVP 固定 42 |
| `status` | 枚举 | `VARCHAR(16)` | 否 | `PENDING`、`RUNNING`、`SUCCESS`、`FAILED` |
| `active_slot` | `String` | `VARCHAR(16)` | 是 | 仅供数据库内部并发控制；`PENDING`、`RUNNING` 时固定为 `GLOBAL`，进入终态后释放为 `null` |
| `sampleCount` | `Integer` | `INT` | 是 | 特征快照确定后写入；非负；`SUCCESS` 时必须 `>= clusterCount`，并等于成员数 |
| `featureSchemaVersion` | `String` | `VARCHAR(64)` | 否 | MVP 为 `community-features-v1`；绑定预处理和特征口径 |
| `parametersJson` | `String`/JSON 映射 | H2 `CLOB`，MySQL `JSON` | 否 | 非敏感参数快照，如 K、种子、预处理选项；不得保存密码、令牌、服务地址或原始特征矩阵 |
| `metricsJson` | `String`/JSON 映射 | H2 `CLOB`，MySQL `JSON` | 是 | 成功后的非敏感指标，如 inertia、PCA 解释方差比例 |
| `startedAt` | `OffsetDateTime` | 带约定的时间列 | 是 | 进入 `RUNNING` 时写入 |
| `finishedAt` | `OffsetDateTime` | 带约定的时间列 | 是 | 进入 `SUCCESS` 或 `FAILED` 时写入 |
| `errorMessage` | `String` | `VARCHAR(1000)` | 是 | 仅供内部持久化失败码/脱敏摘要；不得保存堆栈、凭据、原始异常或内部地址；公开运行详情不直接返回此字段，而映射为 `failure: {code, message}` |
| `createdBy` | `String` | 与用户主键同类型 | 否 | 触发管理员用户 ID；属于审计引用 |
| `createdAt` | `OffsetDateTime` | 带约定的时间列 | 否 | 运行记录创建时间，建议补充的通用审计字段 |

### 2.1 `ClusteringRun` 约束

- `version` 必须有数据库唯一约束。
- `clusterCount >= 2`、`sampleCount IS NULL OR sampleCount >= 0`。
- `algorithm='KMEANS'` 时 `randomState=42`。
- `SUCCESS` 必须有 `startedAt`、`finishedAt`、`sampleCount` 和 `metricsJson`，且内部持久化字段 `errorMessage` 应为 `null`。
- `metricsJson.pcaExplainedVarianceRatio` 固定为两个 `[0, 1]` 内有限数；零方差主成分保存 `0.0`，不得保存 `NaN` 或无穷大。
- `FAILED` 必须有 `finishedAt` 和内部脱敏失败记录；公开 API 只返回结构化 `failure`，不返回持久化 `errorMessage`，且失败运行不能成为 latest。
- `PENDING` 的 `startedAt`、`finishedAt` 应为 `null`；`RUNNING` 必须有 `startedAt` 且 `finishedAt` 为 `null`。
- `parametersJson` 和 `featureSchemaVersion` 在任务开始后不可修改。
- `active_slot` 具有名为 `uk_clustering_runs_active_slot` 的唯一约束，并具有名为 `ck_clustering_runs_active_slot_state` 的状态一致性 `CHECK`：`PENDING`、`RUNNING` 时必须为 `GLOBAL`，`SUCCESS`、`FAILED` 时必须为 `null`。
- `active_slot` 由数据库原子保证最多只有一个活动运行。多个终态运行可以共存，因为数据库唯一约束允许多个 `null`。
- `active_slot` 是内部持久化字段，不属于公开 API、`parametersJson`、`metricsJson` 或运行快照。
- 该约束只提供活动运行互斥，不表示或实现排队、自动重试或调度系统。

## 3. `Community`

表示某一运行内的一个 K-Means 簇及其展示摘要。

| 字段 | 建议 Java 类型 | 建议数据库类型 | 可空 | 约束/说明 |
| --- | --- | --- | --- | --- |
| `id` | `String` | `VARCHAR(64)` | 否 | 主键；长度 1 到 64，由 Spring Boot 生成，对外映射为 `communityId` |
| `runId` | `String` | `VARCHAR(64)` | 否 | 外键指向 `ClusteringRun.id` |
| `clusterNo` | `Integer` | `INT` | 否 | 运行内簇编号，范围 `0..clusterCount-1` |
| `name` | `String` | `VARCHAR(100)` | 否 | MVP 由 Spring Boot 按 `社区 {clusterNo + 1}` 生成；不承担跨版本稳定身份 |
| `description` | `String` | `VARCHAR(500)` | 是 | `topInterests` 非空时为 `主要兴趣：...`，为空时为 `null`；不得虚构浏览行为或敏感属性 |
| `memberCount` | `Integer` | `INT` | 否 | 正整数；必须等于该社区成员记录数 |
| `topInterestsJson` | `String`/JSON 映射 | H2 `CLOB`，MySQL `JSON` | 否 | 最多 3 项的代表性兴趣字符串数组；无值保存 `[]`，仅来自输入兴趣统计 |
| `color` | `String` | `VARCHAR(16)` | 否 | Spring Boot 按 API 契约的 `community-display-v1` 固定调色板和 `clusterNo` 生成 |

### 3.1 `Community` 约束

- 同一运行中的 `clusterNo` 唯一：`UNIQUE(runId, clusterNo)`。
- `clusterNo >= 0`；保存成功结果前由应用层同时校验 `clusterNo < ClusteringRun.clusterCount`。
- `memberCount > 0`。标准 K-Means 成功结果不保存空社区。
- 同一成功运行的社区记录数必须等于 `clusterCount`。
- `topInterestsJson` 必须是字符串数组，不能包含密码、令牌、内部向量或推断出的敏感标签。
- Python 只负责返回确定排序的 `topInterests`；`id`、`name`、`description` 和 `color` 均由 Spring Boot 按 API 契约生成，禁止由浏览器或 Python 请求覆盖。

## 4. `CommunityMember`

表示一个用户在一个运行版本中的唯一硬聚类归属和可视化位置。

| 字段 | 建议 Java 类型 | 建议数据库类型 | 可空 | 约束/说明 |
| --- | --- | --- | --- | --- |
| `id` | `String` | `VARCHAR(64)` | 否 | 主键；长度 1 到 64，对外匿名散点映射为 `pointId` |
| `runId` | `String` | `VARCHAR(64)` | 否 | 外键指向 `ClusteringRun.id`；冗余保存以支持唯一约束和高效按版本查询 |
| `communityId` | `String` | `VARCHAR(64)` | 否 | 外键指向 `Community.id` |
| `userId` | `String` | 与用户主键同类型 | 否 | 外键/逻辑引用用户账号；只用于关联，不存密码或认证信息 |
| `coordinateX` | `Double`/`BigDecimal` | `DOUBLE` 或定点数 | 否 | PCA x 坐标，范围 `[0, 100]`，必须为有限数 |
| `coordinateY` | `Double`/`BigDecimal` | `DOUBLE` 或定点数 | 否 | PCA y 坐标，范围 `[0, 100]`，必须为有限数 |
| `distanceToCenter` | `Double`/`BigDecimal` | `DOUBLE` 或定点数 | 否 | 标准化特征空间中到中心的距离，非负且有限 |
| `assignedAt` | `OffsetDateTime` | 带约定的时间列 | 否 | 归属持久化时间 |

### 4.1 `CommunityMember` 约束

- 同一运行中 `userId` 唯一：`UNIQUE(runId, userId)`。这是“一名用户在一个版本只能属于一个社区”的数据库兜底。
- `coordinateX BETWEEN 0 AND 100`，`coordinateY BETWEEN 0 AND 100`。
- `distanceToCenter >= 0`；应用层还必须拒绝 `NaN` 和无穷大。
- `communityId` 指向的社区必须属于同一个 `runId`。建议通过包含运行 ID 的复合外键实现；若 H2/JPA 映射成本过高，至少在应用层保存前验证并用集成测试覆盖。
- 成功运行中成员总数必须等于 `ClusteringRun.sampleCount`，每个社区实际成员数必须等于 `Community.memberCount`。

## 5. 实体关系说明

关系如下：

- `ClusteringRun 1 -> K Community`：一个运行包含固定 K 个社区，一个社区只能属于一个运行。
- `ClusteringRun 1 -> N CommunityMember`：一个运行保存全部有效用户的唯一归属。
- `Community 1 -> N CommunityMember`：一个社区包含一个或多个成员。
- `UserAccount 1 -> N CommunityMember`（跨版本）：同一用户可在不同运行版本各有一条成员记录，但同一运行最多一条。
- `CommunityMember.runId` 与其 `Community.runId` 必须一致，禁止跨运行引用。

建议逻辑结构：

```text
ClusteringRun (id, version, status)
  ├── Community (runId, clusterNo) [同一 run 唯一]
  │     └── CommunityMember (communityId, runId, userId)
  └── CommunityMember (runId, userId) [同一 run 唯一]

UserAccount (id)
  └── CommunityMember.userId [仅派生数据引用，绝不反向级联删除账号]
```

## 6. 建议索引

除主键和唯一约束自动产生的索引外，建议：

| 表/实体 | 索引 | 用途 |
| --- | --- | --- |
| `ClusteringRun` | `UNIQUE(version)` | 按版本定位并保证版本唯一 |
| `ClusteringRun` | `uk_clustering_runs_active_slot: UNIQUE(active_slot)` | 数据库原子保证最多一个 `PENDING`/`RUNNING` 活动运行；允许多个 `active_slot IS NULL` 的终态运行共存 |
| `ClusteringRun` | `(status, finishedAt)` | 查找最近一次成功运行；查询条件固定 `status='SUCCESS'`，按 `finishedAt DESC` |
| `ClusteringRun` | `(createdAt)` | 管理员按创建时间查看运行历史 |
| `Community` | `UNIQUE(runId, clusterNo)` | 保证同一运行簇编号唯一 |
| `Community` | `(runId)` | 加载某运行的社区 |
| `CommunityMember` | `UNIQUE(runId, userId)` | 保证一人一社区并支持 `me` 查询 |
| `CommunityMember` | `(communityId, id)` | 稳定分页查询社区成员 |
| `CommunityMember` | `(runId, communityId)` | 统计/校验运行内各社区成员数 |
| `CommunityMember` | `(userId, runId)` | 查询用户跨版本归属；若仅查 latest，可由唯一索引和运行 ID 满足 |

实现前应查看数据库实际执行计划，避免为小型本地数据重复创建等价索引。

## 7. 状态流转

合法状态流转：

```text
PENDING -> RUNNING -> SUCCESS
                   -> FAILED
PENDING -----------> FAILED
```

规则：

- 新运行只能以 `PENDING` 创建。
- 创建 `PENDING` 运行时必须将 `active_slot` 设为 `GLOBAL`；若已有活动运行，`uk_clustering_runs_active_slot` 使创建原子失败。
- 执行开始时原子更新为 `RUNNING` 并写入 `startedAt`。
- 只有 `PENDING` 或 `RUNNING` 可转为 `FAILED`。
- 只有 `RUNNING` 可转为 `SUCCESS`。
- 转为 `SUCCESS` 或 `FAILED` 时必须在同一事务中把 `active_slot` 置为 `null`，释放 active slot。
- `SUCCESS` 和 `FAILED` 是终态，不允许回退或被复用；若调用方另行发起新的运行，必须创建新运行和新版本。此状态模型本身不提供自动重试。
- 状态更新应带期望旧状态条件，避免重复执行器覆盖结果。
- `FAILED` 运行不得覆盖、删除或改变最近一次 `SUCCESS` 结果。

## 8. latest 选择规则

- latest 不是单独可被失败任务覆盖的可变指针；MVP 推荐按 `status='SUCCESS'` 且 `finishedAt` 最大查询。
- 如 `finishedAt` 可能相同，使用稳定次序，例如 `finishedAt DESC, createdAt DESC, id DESC`。
- 只有社区数等于 K、成员数等于 `sampleCount` 且成功事务已提交的运行才能被置为 `SUCCESS`。
- `PENDING`、`RUNNING`、`FAILED` 永远不参与 latest 查询。
- 新运行失败时，查询仍返回此前最近的 `SUCCESS`；如果从未成功过，则 API 返回 `NO_SUCCESSFUL_RUN`。

## 9. 事务边界

### 9.1 任务创建与开始（目标异步契约，尚未实现公开 POST）

- 目标契约中，创建 `ClusteringRun(PENDING)` 使用短事务，同时写入 `active_slot='GLOBAL'`；由 `uk_clustering_runs_active_slot` 在数据库中原子拒绝第二个活动运行，登记事务提交后才向调用方返回 `202 Accepted`。
- 当前 `POST /api/v1/admin/community-clustering/runs`、后台执行器、提交拒绝处理和重启恢复尚未实现；现有 `CommunityClusteringOrchestrator` 为同步阻塞执行，不能直接包装成 `202` Controller，也不能把阻塞完成后的响应描述为“仅已登记”。
- `PENDING -> RUNNING` 使用独立短事务和条件更新；不要在远程 Python 调用期间持有数据库事务或锁。
- 特征聚合与 Python 调用在数据库长事务之外执行。需要记录输入数据口径和 `sampleCount`，保证结果可追踪。

### 9.2 成功事务边界

Python 返回后，Spring Boot 先在事务外完成结构和数值校验。随后在一个数据库事务中：

1. 锁定或条件读取仍为 `RUNNING` 的运行记录。
2. 按 API 契约为每个 `clusterNo` 生成 `Community.id`、名称、描述和颜色，并写入该运行的全部 `Community`。
3. 写入该运行的全部 `CommunityMember`。
4. 复核社区数量、总成员数、社区成员计数和唯一约束。
5. 写入 `metricsJson`、`sampleCount`、`finishedAt`，清空内部持久化字段 `errorMessage`。
6. 最后将状态更新为 `SUCCESS`、把 `active_slot` 置为 `null` 以释放 active slot，并提交。

任何一步失败都回滚本次社区、成员及成功状态，禁止出现可被 latest 读取的半成品。

### 9.3 失败事务边界

- 特征聚合、Python 调用、返回校验或成功持久化失败时，先确保成功事务已回滚。
- 再使用独立短事务，把仍处于 `PENDING`/`RUNNING` 的运行更新为 `FAILED`，写入 `finishedAt` 和经过截断、脱敏的内部失败记录，同时把 `active_slot` 置为 `null` 以释放 active slot。查询 API 将该记录解析为 `failure: {code, message}`，绝不直接公开持久化内容。
- 失败更新不得删除或修改任何既有 `SUCCESS` 运行、社区或成员。
- 如果连失败状态事务也失败，应由日志/监控记录待恢复事件；不得把该运行误标为成功。

## 10. 删除与保留策略

- MVP 不提供公开删除聚类运行的 API。
- 若后续增加管理员清理：删除某个运行可以只对派生数据采用 `ClusteringRun -> Community/CommunityMember` 的受控级联，且必须显式授权并在单事务执行。
- `CommunityMember.userId -> UserAccount.id` 必须使用 `RESTRICT`/`NO ACTION` 或等价安全策略；绝不能配置从成员、社区或运行到用户账号的级联删除。
- 删除用户账号是独立的账号生命周期操作。如业务允许删除用户，应先按明确策略清理或匿名化其聚类成员引用；不能因删除社区结果意外删除账号，也不能因悬空外键静默破坏历史一致性。
- 任何清理任务都不得删除当前 latest 成功版本，除非已有明确替代版本并经过单独审批。

## 11. 后续 MySQL 迁移注意事项

- **JSON 类型：** H2 阶段可用 `CLOB` 保存 JSON 文本；MySQL 建议使用原生 `JSON`。迁移前验证 `parametersJson`、`metricsJson`、`topInterestsJson` 都是有效 JSON，并避免依赖 H2 特有 JSON 行为。
- **时间语义：** MySQL `TIMESTAMP`/`DATETIME` 的时区处理不同。建议应用统一使用 `OffsetDateTime` 并明确以 UTC 持久化、API 输出带时区；验证微秒精度和排序稳定性。
- **标识符与长度：** 显式指定表名、列名和 `VARCHAR` 长度，避免 H2 与 MySQL 的保留字、大小写和自动命名差异。
- **约束验证：** 确认 MySQL 实际版本执行 `CHECK` 约束；即使数据库支持，应用层仍要校验 K、坐标范围、有限数和状态一致性。
- **复合外键：** 若采用 `(communityId, runId)` 复合外键，MySQL 被引用列必须有匹配的唯一索引；迁移脚本需按正确顺序创建索引和外键。
- **删除规则：** 显式声明所有外键的 `ON DELETE` 行为，尤其保证用户外键为 `RESTRICT`/`NO ACTION`，只允许聚类派生层内部的受控级联。
- **浮点特殊值：** JSON 和数据库均不应接受 `NaN`/无穷大。进入持久化层前校验有限数；根据展示精度决定使用 `DOUBLE` 或 `DECIMAL`，并编写边界测试。
- **索引长度与字符集：** 使用 `utf8mb4`，确认版本和联合索引长度在目标 MySQL 版本限制内。
- **唯一性与并发：** 在 MySQL 上验证 `UNIQUE(runId, userId)`、`UNIQUE(runId, clusterNo)` 的并发冲突行为和事务隔离级别。
- **迁移工具：** 后续应使用项目统一的版本化数据库迁移方案，不依赖生产环境自动建表；引入任何新生产依赖前需单独审批。
- **迁移演练：** 用 H2 导出的脱敏测试数据执行结构迁移、JSON 转换、计数对账、latest 查询和回滚演练；MySQL 迁移不属于第一版交付。
