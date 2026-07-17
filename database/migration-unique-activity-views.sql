-- 浏览量由重复访问次数改为按登录用户去重的全生命周期访问人数。
-- 旧的累计次数无法还原到具体用户，因此迁移时从 0 重新开始统计。

SET NAMES utf8mb4;
USE campus_activity;

CREATE TABLE IF NOT EXISTS activity_view (
  id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  activity_id BIGINT      NOT NULL,
  user_id     VARCHAR(32) NOT NULL,
  viewed_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_view_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
  CONSTRAINT fk_view_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
  UNIQUE KEY uk_activity_view_user (activity_id, user_id),
  INDEX idx_activity_view_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE activity SET view_count = 0;
UPDATE activity_analysis SET view_count_snapshot = 0;
