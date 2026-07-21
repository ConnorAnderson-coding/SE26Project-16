## A3 校园活动一站式服务平台

### 前端认证与社区聚类接入

前端目录为 `campus-activity`。本地开发时先启动默认监听 `8080` 的 Spring Boot，
再在前端目录运行 `npm.cmd run dev`；Vite 会把同源相对路径 `/api` 代理到
`http://localhost:8080`。生产构建仍只使用同源 `/api`，浏览器不会直接访问 Python
聚类服务。

认证使用 Spring Security HTTP Session，不使用 JWT、Bearer Token 或 localStorage
身份。所有 API 请求都发送 `credentials: "include"`；写请求先通过
`GET /api/auth/csrf` 取得 `token` 与后端返回的 `headerName`，CSRF 信息只保存在页面
内存中。登录成功和页面刷新后均通过 `GET /api/auth/me` 取得可信用户与角色；注册固定
创建 student，不接受前端传入角色。管理员账号必须由后端已有数据提供，前端没有硬编码
管理员账号。

- 登录用户社区结果：`/community`
- 管理员聚类任务：`/admin/community-clustering`

管理员 POST 成功时返回 `202 Accepted` 和 `Location`，这只表示任务已持久化接受；页面会
串行轮询该 Location 对应的运行详情，直到 `SUCCESS` 或 `FAILED`。`409 RUN_CONFLICT`
表示已有任务正在处理，`503 CLUSTERING_SERVICE_UNAVAILABLE` 表示执行功能关闭，但历史
社区查询仍可使用。POST 不会自动重试。

管理员页面会从 `GET /api/v1/admin/community-clustering/runs` 的第 0 页恢复最新
`PENDING`/`RUNNING` 任务，因此刷新不会重新 POST，也不依赖 sessionStorage 或
localStorage 保存 run。运行历史和成员列表默认每页 20 条、最大 100 条。管理员成员区域只
从 `latest`（最新 `SUCCESS`）的社区进入；当前不提供任意历史 run 到社区列表的接口。
成员响应仅含用户 ID、姓名、学院、年级和聚类点字段，不返回密码、角色、联系方式、关系或
完整兴趣。Python 执行关闭时 POST 返回 503，但运行历史和已保存社区成员仍可查询。

### 1. 功能性需求：

1. 用户信息管理：支持师生注册登录，维护兴趣标签、学院年级、可参与时间等个人档案。

2. 活动策划：任何师生都成为活动组织者，他们可以进行活动策划，包括活动类别、内容、起止时间、地点、海报等。

3. 活动推荐与语义检索

    - 支持按兴趣、时间、地点、热度等维度筛选活动。
    - **语义检索（已实现）**：Elasticsearch + GTE hybrid（BM25 ∥ kNN，τ=0.90）；见 [`检索与推荐算法流程.md`](检索与推荐算法流程.md)。
    - **智能推荐（已实现）**：喜好向量 kNN + 硬过滤 + 社交/热度/时间加权；见 [`检索与推荐算法流程.md`](检索与推荐算法流程.md) §七。

4. 活动报名：任何师生都可报名发布后的活动，他们可以收藏和报名活动，并查看报名状态。活动组织者进行报名审核。

5. 活动签到：活动参与者使用二维码、定位或动态口令签到。

6. 活动记录：活动结束后，活动组织者支持发布照片、总结。

7. 活动反馈：活动参与者进行评价与反馈。

### 2. 非功能性需求：

1. 客户端为 Web 浏览器。
2. 兼容性：适应用于客户端不同的分辨率/尺寸。
3. 性能：数据量不少于10k，在100并发的场景下，响应时间<3s。

### 进阶需求：

3. 与上海交通大学的单点登录系统进行集成。
4. 对报名转化率、到场率、用户评价和传播路径进行智能分析，并给出下一次活动改进建议。
5. 设计社区聚类算法，根据师生的行为日志和个人信息，对师生进行聚类，自动划分出用户社区，并进行可视化展示。
6. 其他创意的功能。

### 文档与报告

- 算法流程：[`检索与推荐算法流程.md`](检索与推荐算法流程.md)、[`热度算法流程.md`](热度算法流程.md)
- 设计说明：[`doc/`](doc/)（技术选型、功能实现计划）
- 实验与测试报告：[`report/`](report/)（阈值实验、推荐打分实验、向量 MDS 可视化等）

---

## 一键启动（推荐）

**前置条件**：已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)、JDK 17+、Node.js 18+。

在项目根目录执行：

```powershell
.\start.ps1
```

将自动完成：

1. **Docker 基础设施** — MySQL（建库 + seed）、Redis、Elasticsearch、Kibana
2. **ES 初始化** — 创建 `campus_activities` 索引、部署 GTE 模型 `campus_gte`（首次约 5–15 分钟）
3. **后端** — 新窗口启动 Spring Boot（`:8080`），空索引时自动从 MySQL 重建活动索引
4. **前端** — 新窗口启动 Vite（`:5173`）

访问：

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api/v1 |
| Kibana | http://localhost:5601 |

演示账号：`524030910001` / `123456`（学生）

### 常用参数

```powershell
.\start.ps1 -SkipDeploy          # 仅启动前后端（Docker 已在运行）
.\start.ps1 -InfraOnly           # 仅部署基础设施，不启动应用
.\start.ps1 -ForceRecreateDb     # 清空数据卷后重新建库并启动
.\start.ps1 -SkipEmbedding       # 跳过 GTE 模型下载（可稍后重新 deploy）
```

仅部署基础设施（不启动应用）：

```powershell
cd database
.\deploy.ps1
```

基础设施与排障详见 [`database/README.md`](database/README.md)。
