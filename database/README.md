# 数据库与基础设施

> **推荐**：在项目根目录执行 `.\start.ps1` 一键完成 Docker 建库、ES 初始化、后端与前端启动。详见根目录 [README.md](../README.md)。

## 脚本一览

| 脚本 | 用途 |
|------|------|
| [`../start.ps1`](../start.ps1) | **一键启动**（基础设施 + 前后端） |
| [`deploy.ps1`](deploy.ps1) | 仅部署 Docker + ES 初始化 |
| [`init-es.ps1`](init-es.ps1) | 创建 ES 索引、部署 GTE、注册 pipeline |
| [`reload-demo-data.ps1`](reload-demo-data.ps1) | 修改 seed 后重导 MySQL 并清 Redis |
| [`verify-phase0.ps1`](verify-phase0.ps1) | Phase 0 基础设施验收（开发/CI 用） |

## 使用 Docker Compose

```powershell
cd database
docker compose --profile clustering up -d
```

服务信息：
- MySQL: `localhost:3306`，数据库 `campus_activity`，用户 `campus` / `campus123`
- Redis: `localhost:6379`（后端 Spring Cache 缓存，key 前缀 `campus:`）
- Elasticsearch: `localhost:9200`（语义检索 / 活动推荐，索引 `campus_activities`）
- Kibana: `localhost:5601`（ES 调试与 Dev Tools）
- Clustering service: `localhost:8000`（仅供 Spring Boot 调用的内部 FastAPI）

首次启动会自动执行 `schema.sql` 和 `seed.sql`。

聚类容器位于可选 `clustering` profile，由 `../clustering-service/Dockerfile` 构建，健康检查为
`GET /internal/v1/health`。后端在宿主机开发运行时使用
`COMMUNITY_CLUSTERING_URL=http://127.0.0.1:8000`；不要将该端口作为浏览器公开 API。

### Elasticsearch（检索已落地 · 推荐待复用）

对应 [`技术选型.md`](../doc/技术选型.md) 与 [`检索与推荐算法流程.md`](../检索与推荐算法流程.md)：

- **语义检索（可用）**：BM25（IK）+ **GTE** dense kNN（`campus_gte`，512 / cosine）+ 绝对阈值 τ=0.90 + 加权相关度 Hybrid。
- **智能推荐（可用）**：复用同一索引 / 向量做喜好向量 kNN；硬过滤与多因子加权见 [`检索与推荐算法流程.md`](../检索与推荐算法流程.md) §七。

**内存要求**：建议 Docker Desktop 为 Elasticsearch 分配 **≥ 2GB** 内存（启用 ML / GTE 时）。

仅启动 ES（含 IK 分词插件的自定义镜像）：

```powershell
cd database
docker compose up -d --build elasticsearch
```

初始化索引并部署 GTE `campus_gte`（`thenlper/gte-small-zh`，512 维；中文专用 small，经 Eland **8.15.0** 导入，约 5–15 分钟）：

> **从 E5 切换到 GTE**：维度 384→512，必须 `.\init-es.ps1 -ForceRecreateIndex`，再 `POST /api/v1/search/index/rebuild`。阈值已按 gte-small-zh 重测为 0.90。  
> **国内网络**：默认 `HF_ENDPOINT=https://huggingface.co`；`eland:latest` 与 ES 8.15 不兼容，须用 `docker.elastic.co/eland/eland:8.15.0`。
> 若 `huggingface.co` 不可用，可改用其它可访问的镜像源：
> `.
init-es.ps1 -HfEndpoint "https://<your-endpoint>"`


```powershell
cd database
.\init-es.ps1
# 仅建索引、跳过 GTE：.\init-es.ps1 -SkipEmbedding
# 指定可用 Hugging Face endpoint：
# .\init-es.ps1 -HfEndpoint "https://huggingface.co"
```

或使用一键部署（含上述步骤）：

```powershell
cd database
.\deploy.ps1
# 清空重来：.\deploy.ps1 -ForceRecreateDb
```

验证：

```powershell
curl http://localhost:9200
curl http://localhost:9200/campus_activities/_count
```

### 索引构建（MySQL → ES）

**默认方式：后端启动时自动重建**

`app.elasticsearch.auto-rebuild-on-startup=true`（默认开启）。`.\start.ps1` 启动后端后，若 ES 索引为空会自动从 MySQL bulk 同步。

**手动全量重建**（管理员）：

```powershell
# admin001 / 123456 登录后
POST http://localhost:8080/api/v1/search/index/rebuild
```

**无需后端的 bulk 导入**（仅开发调试）：

```powershell
cd database
.\init-es.ps1 -SkipEmbedding -SeedDocuments
```

新建/更新/删除活动时会自动同步到 ES（非 draft 状态）。

> **注意**：`.\init-es.ps1 -ForceRecreateIndex` 会清空索引。重建 mapping 后重启后端即可自动 re-bootstrap；或手动 POST rebuild。

### Kibana 分词演示

```powershell
docker compose up -d kibana
```

打开 http://localhost:5601/app/dev_tools ，按 `database/elasticsearch/kibana-analyze-demo.md` 中的请求逐步执行，对比 `ik_max_analyzer` 与 `ik_smart_analyzer` 的分词差异。

后端预留配置（`application.properties`）：

```properties
spring.elasticsearch.uris=http://localhost:9200
ES_ACTIVITIES_INDEX=campus_activities
```

### 中文乱码排查

若前端从后端获取的中文显示乱码（前端硬编码中文正常），常见有两类原因：

**1. 数据库初始化字符集错误（全部接口乱码）**

MySQL 首次导入 `seed.sql` 时客户端不是 UTF-8，中文被双重编码写入数据库。

修复（会清空数据库）：

```bash
cd database
docker compose down -v
docker compose up -d
```

**2. Redis 缓存了修复前的旧数据（仅部分接口乱码）**

个人中心（`/users/me`）、活动详情、签到页活动选项等走 Redis 缓存的接口仍乱码，而登录、活动列表正常——说明 **数据库已正确，但 Redis 里还有修复前的脏缓存**。

若本机已安装 Redis 并占用 `6379`，后端可能连到本机 Redis 而非 Docker 容器，执行 `docker exec campus-redis redis-cli FLUSHDB` 无效。建议关闭本机 Redis，仅使用 Docker 提供的 Redis。

修复步骤：

```powershell
# 清空本机 Redis 中的项目缓存（若本机 Redis 在 6379）
redis-cli -p 6379 FLUSHALL

cd database
docker compose up -d
```

然后 **重启后端**，浏览器 **退出登录再重新登录**（清除 localStorage 中的旧用户信息）。

项目已在 Docker MySQL 启动参数中强制 `utf8mb4`，并在 `schema.sql` / `seed.sql` 开头执行 `SET NAMES utf8mb4`；后端 JDBC URL 也指定了 `characterEncoding=UTF-8`。

仅启动 Redis：

```bash
cd database
docker compose up -d redis
```

## 更新种子数据后如何重建（MySQL / Redis / ES）

修改 [`seed.sql`](seed.sql) 后，**仅重启容器不会重新导入**（`mysql_data` 卷已存在时跳过 `docker-entrypoint-initdb.d`）。请按下面顺序同步三库：

### 方式 A：一键重导演示数据（推荐）

```powershell
cd database
.\reload-demo-data.ps1          # 重建 MySQL 库表+seed，并 FLUSH Redis
.\init-es.ps1                   # 确认 GTE / mapping；从 E5 切换务必 `.\init-es.ps1 -ForceRecreateIndex`
```

脚本会自动探测后端实际用的库：

| `-Target` | 场景 |
|-----------|------|
| `Auto`（默认） | 若本机 MySQL 占用了 3306 且 Docker 未成功发布端口 → 重导**本机**库；否则重导 Docker |
| `Host` | 强制重导 `127.0.0.1:3306`（`campus` / `campus123`） |
| `Docker` | 强制重导容器 `campus-mysql` |

> **Windows 常见坑**：本机安装了 MySQL 9.x 占着 3306 时，`docker compose` 里写了 `3306:3306` 也可能**发布失败**（`docker port campus-mysql` 为空）。此时 `reload-demo-data.ps1` 若不加 `-Target Host`，以前只更新了容器内数据，后端仍读本机旧库。  
> 另：不要用 `Get-Content | mysql` 导含中文的 SQL（易变成字面 `?`）；本脚本用 `docker cp` / Python UTF-8 stdin。

然后重启后端（`.\start.ps1 -SkipDeploy`），或管理员 POST `/api/v1/search/index/rebuild`。

### 方式 B：彻底清空 Docker 卷后冷启动

```powershell
cd database
# 若要用 Docker 占用 3306：先停掉本机 MySQL 服务
docker compose down -v          # 删除 mysql_data / redis_data / es_data
docker compose up -d
.\init-es.ps1
# 然后 .\start.ps1 -SkipDeploy 启动后端，会自动重建 ES 索引
```

| 存储 | 作用 | 更新 seed 后要做的事 |
|------|------|----------------------|
| **MySQL** | 业务主数据 | `reload-demo-data.ps1`（注意 Host/Docker）或 `down -v` 后冷启动 |
| **Redis** | 详情/热门等缓存 | `FLUSHALL`（脚本已做）；否则可能看到旧活动 |
| **Elasticsearch** | 检索向量索引 | 重启后端自动 bootstrap；或 POST rebuild |

---

## 手动分步启动

若不用 `start.ps1`，可按序执行：

```powershell
cd database && .\deploy.ps1          # Docker + ES
cd backend && .\mvnw.cmd spring-boot:run
cd frontend && npm install && npm run dev
```

默认连接 `localhost:3306/campus_activity`，Redis `localhost:6379`，ES `localhost:9200`。  
开发环境 Vite 将 `/api` 代理到 `http://localhost:8080`。

## 演示账号

| 账号 | 密码 | 角色 |
|------|------|------|
| 524030910001 | 123456 | 学生 |
| 524030910002 | 123456 | 学生 |
| T001 | 123456 | 教师 |
| admin001 | 123456 | 管理员 |
