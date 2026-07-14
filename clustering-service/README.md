# 社区聚类计算服务

该目录提供社区聚类迭代 2 的独立 Python 计算服务。服务只接受 Spring Boot
组织的不可变特征快照，不访问数据库，不承担公开权限校验、任务编排或结果持久化。

## 接口

- `POST /internal/v1/clustering/run`：执行特征预处理、标准化、K-Means 和 PCA。
- `GET /internal/v1/health`：返回进程及聚类组件健康状态。

该服务是受控内部计算服务，FastAPI 的交互文档和 OpenAPI 路由已在应用配置中有意关闭。
因此 `/docs`、`/redoc` 和 `/openapi.json` 返回 404 是预期行为；
`/internal/v1/health` 保持可用，且不暴露内部配置。

请求和响应字段、错误码及退化行为以 `docs/community-clustering-api.md` 为准。
Python 只返回簇编号、用户关联键、坐标、中心距离、社区计数、代表性兴趣及必要指标。
它不生成或返回 `communityId`、`pointId`、社区名称、描述或颜色。

## 计算规则

- 特征模式固定为 `community-features-v1`，算法固定为 `KMEANS`。
- `randomState` 为必填字段，且当前只允许为 `42`；K-Means 明确使用 `n_init=10`。
- 所有计数字段使用严格非负 int32，范围为 `0..2147483647`，拒绝布尔值、浮点数、字符串和超界整数。
- 服务内部复制样本并按 `userId` 升序排序后计算，输入数组排列不影响逻辑结果，成员结果也按 `userId` 升序返回。
- 类别集合按字符串排序，多值字段使用确定性多热编码；稀疏活动类别计数字典按本次快照的键并集对齐。
- 无平均评分时使用数值 `0` 填充，并增加独立的是否有评分指示特征，避免把缺失与真实评分混为同一口径。
- 所有特征完成编码后使用 `StandardScaler`。
- 用户到中心的距离在标准化特征空间计算。
- PCA 最多计算两个有效主成分；缺少的轴补零，再按轴映射到 `[0, 100]`。
- 投影轴精确零范围或位于绝对容差 `1e-12`、相对容差 `1e-9` 内时，该轴坐标全部为 `50.0`；全退化时解释方差比例为 `[0.0, 0.0]`。
- 不同特征行少于 K 时返回 `CLUSTERING_COMPUTATION_FAILED`，原因为 `INSUFFICIENT_DISTINCT_FEATURE_ROWS`。
- 所有输入和输出数值都必须有限，不接受 `NaN` 或无穷大。

## 本地安装与运行

运行环境需要 Python 3.11 或更高版本。本项目不会自动安装依赖。由开发者明确决定后，
可在本目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
python -m uvicorn app.main:app
```

运行测试：

```powershell
python -m pytest
```

## 明确不包含

- Spring Boot 或浏览器集成
- 数据库、Redis 或定时任务
- 认证、令牌或公开 API
- 社区留言板或推荐功能
- `communityId`、`pointId` 及社区展示元数据生成
