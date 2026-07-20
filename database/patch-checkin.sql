USE campus_activity;

ALTER TABLE activity
  ADD COLUMN latitude DOUBLE NULL AFTER check_in_code,
  ADD COLUMN longitude DOUBLE NULL AFTER latitude,
  ADD COLUMN check_in_radius_m INT NOT NULL DEFAULT 200 AFTER longitude;

CREATE TABLE IF NOT EXISTS check_in (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  activity_id BIGINT       NOT NULL,
  user_id     VARCHAR(32)  NOT NULL,
  method      VARCHAR(16)  NOT NULL COMMENT 'qrcode/location/password',
  checked_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  latitude    DOUBLE       NULL,
  longitude   DOUBLE       NULL,
  distance_m  DOUBLE       NULL,
  CONSTRAINT fk_check_in_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
  CONSTRAINT fk_check_in_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  UNIQUE KEY uk_check_in_activity_user (activity_id, user_id),
  INDEX idx_check_in_activity_time (activity_id, checked_at),
  INDEX idx_check_in_user_time (user_id, checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
