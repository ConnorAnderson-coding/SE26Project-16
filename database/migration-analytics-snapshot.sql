-- Activity Analysis Snapshot Migration
-- 目的：把"加入分析列表时一次性读取的浏览量/报名量/收藏量"持久化为快照
-- 加入分析列表时一次性读取，之后不再随 activity 行变化而变化
-- 防止 viewCount 持续累加、signupCount 受报名/审核影响、favoriteCount 受收藏操作影响
-- 分析任务首次执行时生成，后续展示直接读取该快照

SET NAMES utf8mb4;

USE campus_activity;

ALTER TABLE activity_analysis
  ADD COLUMN view_count_snapshot     INT          NULL COMMENT '冻结时的浏览量',
  ADD COLUMN signup_count_snapshot   INT          NULL COMMENT '冻结时的报名量',
  ADD COLUMN favorite_count_snapshot INT          NULL COMMENT '冻结时的收藏量',
  ADD COLUMN snapshot_at             DATETIME(3)  NULL COMMENT '快照生成时间';
