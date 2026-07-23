# 社区聚类集成日志

## 1. 锁定基线

- `integrationBaseSha = 096479e5db0be183617116f8bb1ec301ebd34eb0`
- `featureReferenceSha = c949a5226d3dda21b200f41cd2e00d6b2bf8f0ec`
- `mergeBaseSha = c949a5226d3dda21b200f41cd2e00d6b2bf8f0ec`
- 集成分支：`integration/community-clustering-v2`
- 本次迁移固定以上述 main 提交为架构基线；迁移期间不自动合入后续 main 变更。

## 2. main 实际技术基线

- 后端目录：`backend`；根包：`com.example.demo`。
- Java：25；Spring Boot：4.1.0；构建工具：Maven 3.9.16（仓库同时提供 Maven Wrapper，但当前 Windows Wrapper 无法正常启动）。
- 认证：Spring Security stateless JWT，Bearer Token；权限由 `UserPrincipal` 映射为 `ROLE_<ROLE>`。
- 统一响应：`ApiResponse<T> { code, message, data }`；统一异常入口为 `GlobalExceptionHandler`。
- 生产数据库：MySQL 8，主脚本为 `database/schema.sql` 和 `database/seed.sql`；测试 profile 使用 H2 MySQL mode。
- 用户主表：`user`，主键 `VARCHAR(32)`；核心行为表为 `registration`、`favorite`、`check_in`、`feedback`，活动表为 `activity`。
- 前端：React 19、Ant Design 6、Vite 8、Vitest 4；统一 Axios 客户端为 `frontend/src/services/http.js`，Token 来自 `localStorage` 并通过 `Authorization: Bearer` 发送。
- 浏览器只通过相对路径 `/api/v1` 调用 Spring Boot。

## 3. TechPrototype 与 feature 文档对照

### 3.1 main 正式文档采用项

`096479e` 仅新增 `TechPrototype` 文档，没有修改数据库、后端、前端、启动脚本或测试代码。新增文档明确了以下约束：

- 社区聚类以 `User` 为样本主表，使用用户画像和 `Registration`、`CheckIn`、`Favorite`、`Feedback` 的真实行为。
- Spring Boot 负责数据聚合、内部调用、跨集合校验和事务持久化。
- 独立 Python FastAPI 服务负责标准化、K-Means 和 PCA。
- K-Means 为互斥硬聚类，固定 `randomState=42`，生成稳定 `clusterNo`、中心距离、inertia、成员数、代表兴趣、样本数和特征维度。
- PCA 仅用于二维展示；公开客户端通过 Spring REST JSON 接口访问。
- 浏览器与 Python 服务隔离，Python 服务只通过内网被应用服务器调用。

### 3.2 与 feature 设计一致项

- 一个运行版本内一名用户恰好属于一个社区。
- 状态为 `PENDING/RUNNING/SUCCESS/FAILED`。
- 输入快照、单活动运行约束、失败持久化、启动恢复和最近成功版本保留。
- Python 固定 K-Means、`random_state=42`、标准化、PCA 和 `[0,100]` 坐标。
- Spring 负责权限、业务数据、编排、校验和持久化；Python 不提供公开权限或数据库写入。
- 普通用户只获取匿名散点与自身归属，管理员成员接口采用最小字段集。

### 3.3 冲突与裁决

- feature 使用旧的 `campus-activity/backend`、`com.example.campusactivity`、Spring Boot 3.3.5、Java 17、Session/CSRF、`UserAccount` 与 H2 运行基线；这些内容全部不迁移，必须适配 main 的 `backend`、`com.example.demo`、Spring Boot 4.1.0、Java 25、JWT、`User` 和 MySQL。
- feature 文档把 MySQL 迁移列为后续能力，但本次集成要求直接接入 main 的 MySQL 脚本，因此以本次集成要求和 main 数据库体系为准。
- UML 设计模型一处文字描述“维护用户-活动二分图、增量更新社区划分”，但同页类图注释及软件架构文档均指向异步 K-Means/PCA；迭代二明确使用 K-Means 硬聚类，因此不实现图社区发现或增量划分。
- 软件架构文档列出学院、年级 One-Hot；本次采用版本化特征 schema，并降低画像分组权重，避免学院和年级主导聚类，同时保留可解释的画像信号。
- main 当前确实存在 `ActivityView`，但本次特征仍不使用浏览行为：原迁移约束禁止凭空或未经产品确认引入浏览数据，且 feature 契约未定义其时间窗口与隐私口径。
- 不迁移 feature 的 Session、CSRF、旧认证、旧 SecurityConfig、第二套 User/UserAccount、H2 生产配置或旧前端 HTTP Client。

## 4. 特征与数据策略（阶段 4 目标）

- schema 版本：`community-features-v2`。
- 时间窗口：默认最近 180 天；画像字段不受行为窗口截断。
- 纳入角色：`student`、`teacher`；排除 `admin`。角色本身不直接作为数值特征。
- 冷启动：具有有效画像但无行为的师生保留为样本，行为特征为零；缺失画像按固定空类别处理，不制造数据。
- 计数使用 `log1p`；使用 `approvedRate`、`attendanceRate` 和类别参与比例，避免重复放大总量。
- 兴趣、活动类别、可参与时间使用固定或随运行冻结的 manifest；学院、年级只使用低权重画像组。
- 不读取 `Activity.signupCount`、`favoriteCount`、`checkInCount` 等缓存计数作为用户特征，以真实行为表聚合为准。
- 最终特征列表、权重和 manifest 在阶段 4 实现后补充到本日志。

## 5. 阶段计划

| 阶段 | 交付物 | 状态 |
| --- | --- | --- |
| 0 | 最新 main 基线、TechPrototype 审查、集成日志 | 完成 |
| 1 | 独立 Python clustering-service | 完成 |
| 2 | MySQL 聚类数据模型与 Spring 持久化骨架 | 完成 |
| 3 | Spring 内部 Client、异步状态机、失败与恢复 | 完成 |
| 4 | 基于 main 实体和行为表的 FeatureBuilder | 待开始 |
| 5 | JWT 权限与公开 REST API | 待开始 |
| 6 | 当前 frontend 聚类用户页与管理员页 | 待开始 |
| 7 | 启动脚本、Docker 和文档 | 待开始 |
| 8 | 全量复审、MySQL 与端到端验收 | 待开始 |

## 6. 阶段测试记录

### 阶段 0

- 后端命令：`mvn test`（JDK 25，Redis 已启动）。
- 后端结果：126 项，123 通过、3 失败、0 错误。三个失败均为 main 原始 `FeedbackIntegrationTest` 与 `FeedbackService` 的业务前置条件不一致：测试未创建报名和签到记录却期望反馈提交成功。人工决定继续迁移；后续后端门禁不得增加新的失败。
- 前端命令：`npm.cmd test`。
- 前端结果：7 个测试文件、45 项测试全部通过。
- 前端命令：`npm.cmd run lint`。
- 前端结果：失败；main 原始 `tests/unit/AppContext.test.jsx` 存在 1 个 `react-hooks/rules-of-hooks` 错误，另有既存警告。后续门禁不得增加新的 lint 错误或警告。
- 前端命令：`npm.cmd run build`。
- 前端结果：成功；存在 main 原始的单 chunk 超过 500 kB 警告。
- 依赖风险：main 的 `frontend/package-lock.json` 与 `package.json` 不同步，`npm ci` 失败；阶段 0 使用 `npm install --package-lock=false` 建立本地测试环境，未修改 lockfile。

### 阶段 1

- 文件范围：仅新增独立 `clustering-service`，未修改 Spring、数据库或前端。
- 契约：阶段 1 保留参考实现的 `community-features-v1`，用于锁定独立算法服务行为；阶段 4 根据 main 真实实体重写输入特征时再显式升级为 `community-features-v2`。
- 命令：`python -m pytest`。
- 结果：105 项测试全部通过；覆盖请求校验、错误契约、计数边界、确定性、样本顺序不变性、稳定簇编号、K-Means、PCA 退化、坐标范围和预处理有限值校验。
- 补充门禁：`python -m pip check` 无破损依赖；`python -m compileall -q app tests` 成功。
- 已知非阻断警告：测试环境中的 Starlette `TestClient` 报告 httpx 兼容层弃用提示，来源于依赖内部，不影响当前服务契约。
- 自审结论：Python 服务不访问数据库、Redis 或外部 HTTP，不处理认证，不生成社区展示主键和元数据，且内部文档/OpenAPI 路由按设计关闭。

### 阶段 2

- MySQL：新增 `clustering_run`、`clustering_run_input`、`community_cluster`、`community_membership`，同时更新全量 schema 并提供非破坏性的增量 patch。
- 数据裁决：输入快照使用带 schema 版本的 JSON payload，不固化旧分支 v1 的字段列；成员和输入直接外键关联 main 的 `User`，没有引入 `UserAccount`。
- 数据库约束：固定 `KMEANS`/`random_state=42`、全局单活动运行、run 内唯一用户样本和唯一成员归属、稳定 clusterNo、同 run 社区成员关系、坐标 `[0,100]`、非负中心距离及级联删除运行结果。
- 新增 JPA 门禁：`CommunityClusteringRepositoryTest` 4 项全部通过，覆盖 JSON 快照、完整关联图、单活动运行、单用户唯一归属和非有限坐标拒绝。
- 全量后端：130 项，127 通过、3 失败、0 错误；失败仍全部为阶段 0 已记录的 `FeedbackIntegrationTest`，没有新增失败。
- 真实 MySQL 8.0：因 ECR 与 Docker Hub 镜像鉴权 TLS 超时，改用本机 MySQL 8.0 二进制在系统临时目录初始化全新隔离实例（独立端口，不接触现有服务数据）。增量脚本执行成功，4/4 表创建，`information_schema` 识别 30 个约束，完整运行/快照/社区/成员写入成功，活动槽唯一约束和坐标范围约束均实测生效；实例随后正常关闭。
- 自审调整：将 Hibernate 7 已弃用的 `@Check` 替换为 Jakarta Persistence 标准 `@CheckConstraint`，并统一 `featureDimension > 0` 的 Bean Validation 与数据库约束。

### 阶段 3

- 内部 Client：使用 main 的 Spring `RestClient`/JDK HttpClient，提供独立连接与读取超时；严格要求 JSON UTF-8、HTTP 200、无未知字段、无尾随 token、无非有限数字及无字符串/布尔到数值的隐式转换。
- 错误边界：4xx 保留受控远端错误码，5xx/网络映射为不可用，畸形响应映射为无效契约；持久化的失败信息使用固定安全文案，不写入远端 details、URL、凭据或堆栈。
- 状态机：实现 `PENDING -> RUNNING -> SUCCESS/FAILED`，通过悲观锁和数据库活动槽唯一约束防止并发领取；结果社区、成员、指标和终态在同一事务提交。
- 异步执行：默认关闭；启用时使用单线程有界队列、定时领取、拒绝失败收敛和 shutdown 等待。浏览器仍不直接访问 Python。
- 启动恢复：将遗留 `RUNNING` 运行收敛为 `FAILED/EXECUTION_INTERRUPTED` 并释放活动槽，保留 `PENDING` 供调度器继续领取。
- 阶段 4 接缝：`ClusteringRequestFactory` 仅定义接口，本阶段没有伪造 main 行为数据；FeatureBuilder 将在下一阶段实现。
- 定向门禁：Client、生命周期和 Worker 共 8 项测试全部通过，覆盖严格解码、远端错误、类型拒绝、成功原子持久化、启动恢复和失败收敛。
- 全量后端：138 项，135 通过、3 失败、0 错误；失败仍全部为阶段 0 已记录的 `FeedbackIntegrationTest`，没有新增失败。
- 自审修复：社区预生成 ID 会触发 Spring Data `merge`，因此先 `saveAllAndFlush` 并使用返回的托管实体建立成员外键，避免引用瞬态社区。

## 7. 提交记录

| 阶段 | 提交 SHA |
| --- | --- |
| 0 | `e8341f0864551520978f11d1faa46e2448a81c7f` |
| 1 | `a15d504` |
| 2 | `7a6f569` |
| 3 | 本阶段提交（SHA 在下一阶段日志更新） |

## 8. 剩余风险

- main 原始后端测试存在 3 个已知失败，前端 lint 存在 1 个已知错误；必须采用“零新增失败”对比门禁。
- Maven Wrapper 在当前 Windows 环境无法启动，阶段测试使用系统 Maven 3.9.16 与 JDK 25。
- main 生产配置仍使用 `spring.jpa.hibernate.ddl-auto=update`，但聚类表必须由 MySQL schema/patch 脚本显式建立并验证。
- MySQL、Redis 和 Elasticsearch 启动成本较高；聚类首版不引入 Redis 依赖。
- Docker 镜像源在阶段 2 出现鉴权 TLS 握手超时；MySQL SQL 已通过隔离本机 MySQL 8.0 实例验收，阶段 8 仍需在项目 compose 环境复验。
- synthetic seed 仅用于集成测试，不能据此证明真实业务聚类质量。
- 最终阶段必须重新 fetch 并检查 `origin/main` 是否仍为锁定 SHA；如已漂移，只报告差异，不自动合入。
