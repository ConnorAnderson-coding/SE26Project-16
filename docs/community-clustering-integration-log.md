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
| 1 | 独立 Python clustering-service | 待开始 |
| 2 | MySQL 聚类数据模型与 Spring 持久化骨架 | 待开始 |
| 3 | Spring 内部 Client、异步状态机、失败与恢复 | 待开始 |
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

## 7. 提交记录

| 阶段 | 提交 SHA |
| --- | --- |
| 0 | 本阶段提交（SHA 在下一阶段日志更新） |

## 8. 剩余风险

- main 原始后端测试存在 3 个已知失败，前端 lint 存在 1 个已知错误；必须采用“零新增失败”对比门禁。
- Maven Wrapper 在当前 Windows 环境无法启动，阶段测试使用系统 Maven 3.9.16 与 JDK 25。
- main 生产配置仍使用 `spring.jpa.hibernate.ddl-auto=update`，但聚类表必须由 MySQL schema/patch 脚本显式建立并验证。
- MySQL、Redis 和 Elasticsearch 启动成本较高；聚类首版不引入 Redis 依赖。
- synthetic seed 仅用于集成测试，不能据此证明真实业务聚类质量。
- 最终阶段必须重新 fetch 并检查 `origin/main` 是否仍为锁定 SHA；如已漂移，只报告差异，不自动合入。
