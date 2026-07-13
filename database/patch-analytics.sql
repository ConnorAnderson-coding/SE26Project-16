-- 智能分析与建议模块 — 数据库变更脚本
-- 依赖: check_in 表已存在 (patch-check-in.sql)

-- 新增 activity.view_count 字段（用于计算报名转化率）
-- 注意: 如已执行过此 ALTER 会报错，可忽略
ALTER TABLE activity ADD COLUMN view_count INT NOT NULL DEFAULT 0 AFTER signup_count;

-- 活动分析结果表
CREATE TABLE IF NOT EXISTS activity_analysis (
    id                     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activity_id            BIGINT       NOT NULL,
    -- 核心指标
    signup_rate            DECIMAL(5,1)   NOT NULL COMMENT '报名转化率(%)',
    attendance_rate        DECIMAL(5,1)   NOT NULL COMMENT '到场率(%)',
    avg_rating             DECIMAL(3,2)   NULL     COMMENT '平均评分(1.00-5.00)',
    favorite_conversion    DECIMAL(5,1)   NULL     COMMENT '收藏转化率(%)',
    -- 指标明细 (JSON)
    rating_distribution    JSON           NULL     COMMENT '评分分布: {"1":n,"2":n,"3":n,"4":n,"5":n}',
    check_in_methods_stats JSON           NULL     COMMENT '签到方式统计: {"qrcode":n,"location":n,"password":n}',
    metrics_json           JSON           NULL     COMMENT '完整指标快照，含辅助维度对比数据',
    -- LLM 建议
    suggestions            JSON           NULL     COMMENT '建议列表: [{"id":"","category":"","priority":"","content":""}]',
    suggestion_source      VARCHAR(16)    NOT NULL DEFAULT 'llm' COMMENT '建议来源: llm / rule / fallback',
    suggestion_model       VARCHAR(64)    NULL     COMMENT 'LLM模型名称，如 deepseek-chat',
    -- 时间戳
    generated_at           DATETIME(3)    NOT NULL COMMENT '分析生成时间',
    created_at             DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_analysis_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    UNIQUE KEY uk_analysis_activity (activity_id),
    INDEX idx_analysis_activity (activity_id),
    INDEX idx_analysis_generated (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分析任务调度日志表
CREATE TABLE IF NOT EXISTS analysis_schedule_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_name        VARCHAR(64)   NOT NULL COMMENT '任务名称，如 analyzeEndedActivities',
    activity_id     BIGINT        NULL     COMMENT '关联活动ID，单活动手动触发时填入',
    status          VARCHAR(16)   NOT NULL COMMENT '执行状态: STARTED / SUCCESS / FAILED / SKIPPED',
    duration_ms     INT           NULL     COMMENT '执行耗时(毫秒)',
    error_message   VARCHAR(1000) NULL     COMMENT '错误信息',
    started_at      DATETIME(3)   NOT NULL,
    finished_at     DATETIME(3)   NULL,
    INDEX idx_log_job (job_name, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
