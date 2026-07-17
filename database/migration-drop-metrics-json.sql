-- Drop unused metrics_json column from activity_analysis.
-- 该列在 feat/analytics 引入时计划存整份 ActivityMetrics 备份，但实际从未被读写。
-- 后端 entity 字段已删除，此处负责清理已部署库的孤儿列。

SET NAMES utf8mb4;
USE campus_activity;

ALTER TABLE activity_analysis DROP COLUMN metrics_json;
