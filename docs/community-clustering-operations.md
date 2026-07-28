# 社区聚类运维

## 启动

推荐在仓库根目录运行 `./start.ps1`。database compose 会构建并启动 FastAPI 容器；
应用启动脚本检测到健康服务后直接复用，否则以本地 `.venv` 启动。Spring Boot 需要：

```text
COMMUNITY_CLUSTERING_ENABLED=true
COMMUNITY_CLUSTERING_URL=http://127.0.0.1:8000
COMMUNITY_CLUSTERING_CONNECT_TIMEOUT=2s
COMMUNITY_CLUSTERING_READ_TIMEOUT=30s
```

使用 `./start.ps1 -SkipClustering` 可关闭计算能力；此时管理员 POST 返回 503，历史 GET
和平台其他功能继续工作。不要把内部端口配置为前端 URL。

## 健康与故障

1. `GET http://127.0.0.1:8000/internal/v1/health` 应返回 `UP` 和 v2 schema；
2. 检查 Spring 运行详情的安全失败码，不在公开响应中暴露 Python 地址或堆栈；
3. 应用重启会把遗留 `RUNNING` 收敛为 `FAILED/EXECUTION_INTERRUPTED`，保留 `PENDING`；
4. 当前只支持单 Spring 应用实例，不承诺分布式 exactly-once。

## 验收

- Python：`python -m pytest`；
- 后端：JDK 25 下 `mvn test`，并用 MySQL 8 执行全量/增量 schema；
- 前端：`npm test`、`npm run lint`、`npm run build`；
- 端到端：管理员提交后轮询到 `SUCCESS/FAILED`，普通用户验证 latest/me，学生访问管理员
  路由必须被拒绝，latest 响应不得出现其他用户 ID 或画像。
