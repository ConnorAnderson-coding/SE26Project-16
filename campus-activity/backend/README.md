# Campus Activity Backend

这是校园活动一站式服务平台的 Spring Boot 后端示例，当前目标是完成基本 CRUD 和前端原型常用业务操作。

## 技术栈

- Java 17
- Spring Boot 3.3
- Spring Web
- Spring Data JPA
- H2 内存数据库

## 启动

```bash
cd backend
mvn spring-boot:run
```

服务地址：

- API: `http://localhost:8080/api`
- H2 控制台: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:campus_activity`
- 用户名: `sa`
- 密码: 留空

## 主要接口

### 登录注册

- `POST /api/auth/login`
- `POST /api/auth/register`

### 用户 CRUD

- `GET /api/users`
- `GET /api/users/{id}`
- `POST /api/users`
- `PUT /api/users/{id}`
- `DELETE /api/users/{id}`

### 活动 CRUD 与查询

- `GET /api/activities`
- `GET /api/activities/{id}`
- `POST /api/activities`
- `PUT /api/activities/{id}`
- `DELETE /api/activities/{id}`
- `PUT /api/activities/{id}/record`

`GET /api/activities` 支持 `category`、`status`、`q`、`location`、`tag`、`sort=hot` 查询参数。

### 报名、收藏、签到、反馈

- `GET /api/signups?activityId=1`
- `POST /api/activities/{activityId}/signup?userId=524030910001`
- `PUT /api/signups/{id}/review`
- `GET /api/favorites?userId=524030910001`
- `POST /api/activities/{activityId}/favorite?userId=524030910001`
- `GET /api/checkins?activityId=6`
- `POST /api/activities/{activityId}/checkins?userId=524030910001&method=qrcode`
- `GET /api/feedbacks?activityId=6`
- `POST /api/activities/{activityId}/feedbacks?userId=524030910001`

## 示例请求

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"id":"524030910001","password":"123456"}'
```

```bash
curl -X POST 'http://localhost:8080/api/activities/1/signup?userId=524030910002'
```
