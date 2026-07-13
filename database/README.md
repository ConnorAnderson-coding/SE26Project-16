# 数据库启动说明

## 使用 Docker Compose（推荐）

```bash
cd database
docker compose up -d
```

服务信息：
- MySQL: `localhost:3306`，数据库 `campus_activity`，用户 `campus` / `campus123`
- Redis: `localhost:6379`（后端 Spring Cache 缓存，key 前缀 `campus:`）

首次启动会自动执行 `schema.sql` 和 `seed.sql`。

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

## 智能分析演示数据

数据库和后端启动后，可导入一组独立且可重复执行的分析样例。脚本只会重建
`check_in_code = 'AI-DEMO'` 的演示活动，不会清理其他活动。

```powershell
docker cp .\database\seed-analytics-demo.sql campus-mysql:/tmp/seed-analytics-demo.sql
docker exec campus-mysql sh -c "mysql -ucampus -pcampus123 --default-character-set=utf8mb4 campus_activity < /tmp/seed-analytics-demo.sql"
docker exec campus-redis redis-cli FLUSHDB
.\scripts\analytics-e2e-test.ps1
```

未设置 `DEEPSEEK_API_KEY` 时，建议生成会立即降级为规则模板；设置环境变量并重启后端后，
会调用配置的 LLM API。API Key 不应写入项目配置文件或提交到版本库。
