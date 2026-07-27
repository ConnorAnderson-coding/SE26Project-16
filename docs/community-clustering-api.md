# 社区聚类 API

所有公开端点均位于 Spring Boot `/api/v1`，使用 main 的 Bearer JWT 和
`ApiResponse<T> { code, message, data }`。浏览器不得直接调用 Python 服务。

## 用户端点

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/community-clustering/latest` | 已登录 | 最新成功运行、社区摘要和匿名散点 |
| GET | `/community-clustering/me` | 已登录 | 当前 JWT 用户在最新版本中的归属 |

`latest` 的点仅包含 `pointId`、`x`、`y`、`currentUser`。它不返回其他用户的
ID、姓名、学院、年级、兴趣或中心距离。`me` 不接受 `userId` 参数。

## 管理员端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/community-clustering/runs` | 提交异步运行；Body 只能是 `{"clusterCount": 2}` |
| GET | `/admin/community-clustering/runs?page=0&size=20` | 稳定倒序分页运行历史 |
| GET | `/admin/community-clustering/runs/{runId}` | 运行详情、指标或安全失败摘要 |
| GET | `/admin/community-clustering/communities/{communityId}/members?page=0&size=20` | 最小成员资料分页 |

管理员端点要求 `ROLE_ADMIN`。POST 的身份只来自 JWT；严格拒绝未知字段、重复键、
尾随 JSON、类型强制转换以及 `createdBy/userId/role` 注入。功能关闭时 POST 返回 503，
GET 仍可读取已保存结果。

## 内部 Python 端点

| 方法 | 路径 | 调用者 |
| --- | --- | --- |
| POST | `/internal/v1/clustering/run` | 仅 Spring Boot |
| GET | `/internal/v1/health` | 服务健康检查 |

内部契约固定 `KMEANS`、`randomState=42` 和 `community-features-v2`。Python 不访问
数据库、不验证终端用户、不生成展示主键，也不持久化结果。
