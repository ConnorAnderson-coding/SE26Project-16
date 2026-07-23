# 社区聚类 MVP 边界

MVP 使用最近 180 天 `Registration`、`CheckIn`、`Favorite`、`Feedback` 的真实行为，
结合 `User` 的兴趣、学院、年级和可参与时间，对 `student/teacher` 执行确定性 K-Means。
管理员不进入样本；无行为的合资格用户作为冷启动零行为样本保留。

特征 schema 为 `community-features-v2`：计数使用 `log1p`，包含报名批准率、出席率、
活动类别参与比例、评分与评分存在位；学院和年级 One-Hot 的标准化后权重为 0.35。
PCA 仅负责二维展示，不参与社区身份定义。

不在 MVP 内：图社区发现、增量聚类、自动重试、分布式 lease、消息队列、社区留言板、
浏览行为特征、推断敏感标签，以及浏览器直连 Python。真实聚类质量必须使用经授权的
脱敏业务数据另行评估，synthetic seed 只能证明管道与约束正确。
