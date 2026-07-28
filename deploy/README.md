# 全栈 Docker 部署（Windows 云服务器 / Docker Desktop）

面向「整站容器化 + Nginx 反代」场景，满足课程非功能需求中的高并发访问入口形态：
数据量 ≥ 10k、100 并发、响应时间 < 3s（需结合压测与资源规格验证）。

## 架构

```text
浏览器
  └─ Nginx :80
        ├─ /          → 前端静态资源（Vite build）
        └─ /api/      → Spring Boot :8080
              ├─ MySQL
              ├─ Redis
              ├─ Elasticsearch (+ GTE)
              └─ clustering-service :8000（仅内网）
```

公网建议**只开放 80**。MySQL / Redis / ES / 聚类端口仅绑定 `127.0.0.1`，供本机初始化与排障。

## 前置条件

- Windows Server / Windows 10+，已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)，**Linux 容器**模式
- 建议资源：**CPU ≥ 4 核，内存 ≥ 8GB**（Elasticsearch 默认 `-Xms2g -Xmx2g`）；**4 核 + 16GB 云主机足够**
- 磁盘预留 ≥ 20GB（镜像 + ES 模型 + 数据卷）

## 云服务器部署（Ubuntu 推荐）

本机内存紧张或不支持 Windows 嵌套虚拟化时，使用 **Ubuntu 24.04 + Docker Engine**：

```bash
# 1) 安装 Docker Engine + compose 插件，并设置：
#    sudo sysctl -w vm.max_map_count=262144

# 2) 克隆仓库后配置环境变量
cd deploy
cp .env.example .env
nano .env   # 修改 PUBLIC_BASE_URL / CORS_ORIGINS / JWT_SECRET

# 3) 一键启动（构建 + 起容器 + init-es + 重启 backend 灌索引）
chmod +x ../start.sh ./deploy.sh ../database/init-es.sh
../start.sh
# 或: ./deploy.sh
```

仅初始化 / 重建 ES：

```bash
cd database
./init-es.sh
# 跳过 GTE: ./init-es.sh --skip-embedding
# 国内 HF 慢: HF_ENDPOINT=https://hf-mirror.com ./init-es.sh
```

常用参数：

```bash
./deploy.sh --skip-build          # 不重建镜像
./deploy.sh --skip-es-init        # 跳过 ES 初始化
./deploy.sh --skip-embedding      # 只建索引，不下 GTE
./deploy.sh --force-recreate      # 清空数据卷重建
./deploy.sh --with-kibana
```

访问：`http://<公网IP>`；演示账号：`524030910001` / `123456`。

> Windows 本地开发仍用根目录 `.\start.ps1`（Docker 基础设施 + 本机前后端）。  
> Ubuntu 云主机请用 `./start.sh` / `deploy/deploy.sh`，不要跑 `.ps1`。

## 云服务器部署（推荐：本机内存不足时）

本机内存紧张时，**不要在本机构建镜像**，把代码同步到云主机后再构建：

1. 云主机安装 Docker Engine（Linux），防火墙/安全组放行 **TCP 80**
2. 同步代码（`git clone` / `git pull`）
3. 在云主机执行上面的 `./start.sh` 流程

## 一键部署

```powershell
cd deploy
copy .env.example .env   # 首次
.\deploy.ps1
```

常用参数：

```powershell
.\deploy.ps1 -ForceRecreate     # 清空数据卷重建
.\deploy.ps1 -SkipEmbedding     # 跳过 GTE 下载（稍后补 init-es）
.\deploy.ps1 -SkipEsInit        # 完全跳过 ES 初始化
.\deploy.ps1 -WithKibana        # 额外启动 Kibana（本机 5601）
.\deploy.ps1 -SkipBuild         # 不重建镜像，仅 up
```

访问：`http://localhost`（或云服务器公网 IP）。演示账号：`524030910001` / `123456`。

## 环境变量

见 [`.env.example`](.env.example)。上云前至少修改：

| 变量 | 说明 |
|------|------|
| `PUBLIC_BASE_URL` | 公网访问根地址，如 `http://x.x.x.x` |
| `CORS_ORIGINS` | 与公网 Origin 一致 |
| `JWT_SECRET` | 生产随机长密钥 |
| `MYSQL_PASSWORD` | 生产数据库密码 |
| `JACCOUNT_*` | 需要交大 SSO 时再启用 |

国内构建默认使用阿里云 Maven、npmmirror、清华 PyPI，可在 `.env` 覆盖。

## 与本地开发启动的关系

| 方式 | 用途 |
|------|------|
| 根目录 `.\start.ps1` | 开发：Docker 只跑基础设施，本机跑前后端 |
| `deploy\.\deploy.ps1` | 部署：前后端/基础设施/Nginx **全部容器化** |

两套编排数据卷独立（compose project 名不同），请勿混用同一端口冲突的旧容器；部署前可 `docker compose -f ..\database\docker-compose.yml down`。

## 性能相关配置

Nginx（`frontend/docker/`）：

- `worker_connections 4096`、upstream `keepalive 64`
- gzip、静态资源缓存、`/api/` 反代与连接复用

后端（环境变量 / `application.properties`）：

- `TOMCAT_MAX_THREADS=200`、`TOMCAT_ACCEPT_COUNT=200`
- `HIKARI_MAX_POOL=50`（100 并发下避免连接打满）

当前 `seed.sql` 量级约：用户 ~800+、活动 1500、报名 5k+。若验收要求「数据量不少于 10k」，请以**业务主表合计行数**或指定表为准，不足时用 `database/reload-demo-data.ps1` / 扩种脚本补齐后再压测。

### 简易并发冒烟（PowerShell）

```powershell
$url = "http://localhost/api/v1/auth/login"
$body = '{"username":"524030910001","password":"123456"}'
1..100 | ForEach-Object -Parallel {
  Measure-Command {
    Invoke-RestMethod -Method Post -Uri $using:url -ContentType 'application/json' -Body $using:body
  }
} -ThrottleLimit 100 | Measure-Object TotalMilliseconds -Average -Maximum
```

目标：Average / P95（可用更专业工具如 k6、JMeter）< 3000ms。

## 常用运维

```powershell
docker compose --env-file .env ps
docker compose --env-file .env logs -f backend nginx
docker compose --env-file .env restart backend
docker compose --env-file .env down
```

社区聚类仍需管理员在 Web 端提交一次运行后，学生端才有可视化结果。
