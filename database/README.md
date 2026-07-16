# 数据库启动说明

## 使用 Docker Compose（推荐）

```bash
cd database
docker compose up -d
```

服务信息：
- MySQL: `localhost:3306`，数据库 `campus_activity`，用户 `campus` / `campus123`
- Redis: `localhost:6379`（后端 Spring Cache 缓存，key 前缀 `campus:`）
- Elasticsearch: `localhost:9200`（语义检索 / 活动推荐，索引 `campus_activities`）
- Kibana: `localhost:5601`（ES 调试与 Dev Tools）

首次启动会自动执行 `schema.sql` 和 `seed.sql`。

### Elasticsearch（检索已落地 · 推荐待复用）

对应 [`技术选型.md`](../doc/技术选型.md) 与 [`检索与推荐.md`](../doc/检索与推荐.md)：

- **语义检索（可用）**：BM25（IK）+ **GTE** dense kNN（`campus_gte`，512 / cosine）+ 绝对阈值 τ=0.90 + 加权相关度 Hybrid。
- **智能推荐（可用）**：复用同一索引 / 向量做喜好向量 kNN；硬过滤与多因子加权见 [`检索与推荐.md`](../doc/检索与推荐.md) §6。

**内存要求**：建议 Docker Desktop 为 Elasticsearch 分配 **≥ 2GB** 内存（启用 ML / GTE 时）。

仅启动 ES（含 IK 分词插件的自定义镜像）：

```powershell
cd database
docker compose up -d --build elasticsearch
```

初始化索引并部署 GTE `campus_gte`（`thenlper/gte-small-zh`，512 维；中文专用 small，经 Eland **8.15.0** 导入，约 5–15 分钟）：

> **从 E5 切换到 GTE**：维度 384→512，必须 `.\init-es.ps1 -ForceRecreateIndex`，再 `POST /api/v1/search/index/rebuild`。阈值已按 gte-small-zh 重测为 0.90。  
> **国内网络**：默认 `HF_ENDPOINT=https://hf-mirror.com`；`eland:latest` 与 ES 8.15 不兼容，须用 `docker.elastic.co/eland/eland:8.15.0`。


```powershell
cd database
.\init-es.ps1
# 仅建索引、跳过 GTE：.\init-es.ps1 -SkipEmbedding
```

一键建库 + 启动 ES + 初始化索引：

```powershell
cd database
.\setup-local.ps1 -InitElasticsearch
# 跳过 GTE 部署：.\setup-local.ps1 -InitElasticsearch -SkipEmbedding
```

验证：

```powershell
curl http://localhost:9200
curl http://localhost:9200/campus_activities/_count
```

### 索引构建（MySQL -> ES）

**方式 A：脚本 bulk 导入示例数据（无需后端）**

```powershell
cd database
.\init-es.ps1 -SkipEmbedding -SeedDocuments
# 或单独执行
.\index-seed.ps1
```

**方式 B：后端全量重建（从 MySQL 同步）**

启动后端（`app.elasticsearch.enabled` 默认为 `true`）：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

> 请先保证 Docker 中 Elasticsearch 已就绪（`.\init-es.ps1`）。后端默认启用 ES；暂时不用可设 `$env:ES_ENABLED="false"`。

```powershell
# 管理员登录获取 token 后
curl -X POST http://localhost:8080/api/v1/search/index/rebuild `
  -H "Authorization: Bearer <admin_token>"
```

演示账号 `admin001 / 123456`。

新建/更新/删除活动时会自动同步到 ES（非 draft 状态）。

> **注意**：`.\init-es.ps1 -ForceRecreateIndex` 会清空索引。重建 mapping 后必须再执行一次后端 `rebuild`，否则关键词搜索会命中空索引（结果为 0），而「无关键词」列表仍走 MySQL 正常显示。

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

然后启动后端，用管理员重建 ES 文档向量：

```powershell
# admin001 / 123456
POST http://localhost:8080/api/v1/search/index/rebuild
```

或在实验脚本里加 `--rebuild`：

```powershell
cd ..\backend
python scripts/cosine-threshold-experiment.py --rebuild
```

### 方式 B：彻底清空 Docker 卷后冷启动

```powershell
cd database
# 若要用 Docker 占用 3306：先停掉本机 MySQL 服务
docker compose down -v          # 删除 mysql_data / redis_data / es_data
docker compose up -d            # MySQL 会自动执行 schema.sql + seed.sql
.\init-es.ps1                   # 重新下载/部署 GTE（若卷被清）
# 后端启动后 POST /api/v1/search/index/rebuild
```

| 存储 | 作用 | 更新 seed 后要做的事 |
|------|------|----------------------|
| **MySQL** | 业务主数据 | `reload-demo-data.ps1`（注意 Host/Docker）或 `down -v` 后冷启动 |
| **Redis** | 详情/热门等缓存 | `FLUSHALL`（脚本已做）；否则可能看到旧活动 |
| **Elasticsearch** | 检索向量索引 | **必须** `POST .../search/index/rebuild`（`-ForceRecreateIndex` 后尤其必要） |

### 余弦阈值实验

```powershell
cd backend
python scripts/cosine-threshold-experiment.py --rebuild
# 输出：report/cosine-threshold-experiment.md / .csv
```

---

## 手动建库

1. 创建数据库并执行 `schema.sql`
2. 执行 `seed.sql` 导入示例数据

## 后端启动

**请先确保 Redis 已运行**（后端使用 Spring Cache + Redis 缓存活动详情、热门列表等）。

```bash
cd backend
./mvnw spring-boot:run
```

默认连接 `localhost:3306/campus_activity`，Redis `localhost:6379`。

## 前端启动

```bash
cd campus-activity
npm install
npm run dev
```

开发环境通过 Vite 代理将 `/api` 转发到 `http://localhost:8080`。

## 演示账号

| 账号 | 密码 | 角色 |
|------|------|------|
| 524030910001 | 123456 | 学生 |
| 524030910002 | 123456 | 学生 |
| T001 | 123456 | 教师 |
| admin001 | 123456 | 管理员 |
