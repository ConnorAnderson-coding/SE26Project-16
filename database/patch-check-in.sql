-- 签到记录表
-- 当前签到功能在前端 mock 实现，本表补齐后端持久化

CREATE TABLE IF NOT EXISTS check_in (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activity_id     BIGINT       NOT NULL,
    user_id         VARCHAR(32)  NOT NULL,
    method          VARCHAR(16)  NOT NULL COMMENT '签到方式: qrcode(二维码) / location(定位) / password(口令)',
    check_in_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_checkin_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_checkin_user     FOREIGN KEY (user_id)     REFERENCES `user`(id),
    UNIQUE KEY uk_checkin_activity_user (activity_id, user_id),
    INDEX idx_checkin_activity (activity_id),
    INDEX idx_checkin_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
