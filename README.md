## A3 校园活动一站式服务平台

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

**前置条件**：已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)、JDK 25、Node.js 20+；仅使用 `-SkipDeploy` 且本机没有运行聚类容器时，还需 Python 3.11+。

在项目根目录执行：

```powershell
.\start.ps1
```

将自动完成：

1. **Docker 基础设施** — MySQL（建库 + seed）、Redis、Elasticsearch、Kibana、内部聚类服务
2. **ES 初始化** — 创建 `campus_activities` 索引、部署 GTE 模型 `campus_gte`（首次约 5–15 分钟）
3. **后端** — 新窗口启动 Spring Boot（`:8080`），启用社区聚类内部调用，空索引时自动从 MySQL 重建活动索引
4. **前端** — 新窗口启动 Vite（`:5173`）

访问：

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api/v1 |
| 聚类服务健康检查（仅内部运维） | http://localhost:8000/internal/v1/health |
| Kibana | http://localhost:5601 |

演示账号：`524030910001` / `123456`（学生）

### 常用参数

```powershell
.\start.ps1 -SkipDeploy          # 仅启动前后端（Docker 已在运行）
.\start.ps1 -InfraOnly           # 仅部署基础设施，不启动应用
.\start.ps1 -ForceRecreateDb     # 清空数据卷后重新建库并启动
.\start.ps1 -SkipEmbedding       # 跳过 GTE 模型下载（可稍后重新 deploy）
.\start.ps1 -SkipClustering      # 不启动/启用聚类服务，其余功能仍可运行
```

仅部署基础设施（不启动应用）：

```powershell
cd database
.\deploy.ps1
```

基础设施与排障详见 [`database/README.md`](database/README.md)。

### 社区聚类

- 学生和教师可在 `/community` 查看最新成功版本的匿名散点和自己的社区归属。
- 管理员可在 `/admin/community-clustering` 提交异步聚类任务、查看运行状态和最小成员资料。
- 浏览器始终只访问 Spring Boot；FastAPI 端口是内部计算接口，不承担登录与公开授权。
- 运维、API、数据和验收边界见 [`docs/community-clustering-operations.md`](docs/community-clustering-operations.md)、[`docs/community-clustering-api.md`](docs/community-clustering-api.md) 与 [`docs/community-clustering-data-model.md`](docs/community-clustering-data-model.md)。
