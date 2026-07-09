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
