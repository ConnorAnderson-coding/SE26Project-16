# 社区聚类数据模型

社区聚类复用 main 的 `User`，不创建第二套账号实体。MySQL 8 中新增四张派生表：

- `clustering_run`：运行参数、状态、指标、失败摘要和全局活动槽；
- `clustering_run_input`：提交时不可变的逐用户特征 JSON 快照；
- `community_cluster`：成功运行的 K 个社区及展示摘要；
- `community_membership`：用户在一个运行中的唯一硬归属、PCA 坐标和中心距离。

主要约束包括：运行版本唯一、同时最多一个 `PENDING/RUNNING`、同运行样本顺序和
用户唯一、同运行簇编号唯一、同运行用户归属唯一、坐标 `[0,100]`、距离非负以及
社区/成员运行一致性。派生数据可随运行受控级联删除，但用户外键使用限制删除，绝不
从聚类结果级联删除账号。

latest 由 `status=SUCCESS` 且完成时间最大的运行确定；失败运行不会覆盖上一成功版本。
任务提交冻结全部输入，后台执行和重启恢复都从快照重建请求，不重新读取变化中的画像。

生产结构以 [`database/schema.sql`](../database/schema.sql) 为全量来源，已有数据库使用
[`database/patch-community-clustering.sql`](../database/patch-community-clustering.sql)
增量升级；H2 仅用于自动化测试，不能替代 MySQL 脚本验收。
