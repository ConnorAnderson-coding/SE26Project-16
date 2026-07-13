-- Campus Activity Platform Schema
-- Redis cache key conventions (reserved for future use):
--   activity:detail:{id}
--   activity:list:hot:page:{n}
--   user:profile:{id}

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS campus_activity
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE campus_activity;

CREATE TABLE IF NOT EXISTS `user` (
  id              VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '学号/工号',
  password_hash   VARCHAR(255) NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  role            VARCHAR(16)  NOT NULL DEFAULT 'student' COMMENT 'student/teacher/admin',
  college         VARCHAR(64)  NOT NULL,
  grade           VARCHAR(32)  NOT NULL,
  interests       JSON         NULL COMMENT '兴趣标签数组',
  available_time  JSON         NULL COMMENT '可参与时间数组',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_user_role (role),
  INDEX idx_user_college (college)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity (
  id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title            VARCHAR(200) NOT NULL,
  category         VARCHAR(32)  NOT NULL,
  description      TEXT         NOT NULL,
  start_time       DATETIME(3)  NOT NULL,
  end_time         DATETIME(3)  NOT NULL,
  location         VARCHAR(200) NOT NULL,
  organizer_id     VARCHAR(32)  NOT NULL,
  college          VARCHAR(64)  NOT NULL,
  poster           VARCHAR(500) NULL,
  max_participants INT          NOT NULL DEFAULT 50,
  signup_count     INT          NOT NULL DEFAULT 0,
  view_count       INT          NOT NULL DEFAULT 0 COMMENT '活动详情浏览量',
  favorite_count   INT          NOT NULL DEFAULT 0,
  status           VARCHAR(16)  NOT NULL DEFAULT 'published' COMMENT 'draft/published/ended',
  tags             JSON         NULL,
  check_in_code    VARCHAR(32)  NULL,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_activity_organizer FOREIGN KEY (organizer_id) REFERENCES `user`(id),
  INDEX idx_activity_status (status),
  INDEX idx_activity_category (category),
  INDEX idx_activity_start_time (start_time),
  INDEX idx_activity_organizer (organizer_id),
  INDEX idx_activity_hot (signup_count, favorite_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS registration (
  id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  activity_id BIGINT      NOT NULL,
  user_id     VARCHAR(32) NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_reg_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
  CONSTRAINT fk_reg_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  UNIQUE KEY uk_reg_activity_user (activity_id, user_id),
  INDEX idx_reg_user (user_id),
  INDEX idx_reg_activity_status (activity_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS favorite (
  user_id     VARCHAR(32) NOT NULL,
  activity_id BIGINT      NOT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, activity_id),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
  CONSTRAINT fk_fav_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity_record (
  activity_id  BIGINT       NOT NULL PRIMARY KEY,
  summary      TEXT         NOT NULL,
  photos       JSON         NULL,
  published_at DATETIME(3)  NOT NULL,
  CONSTRAINT fk_record_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedback (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  activity_id BIGINT       NOT NULL,
  user_id     VARCHAR(32)  NOT NULL,
  rating      INT          NOT NULL,
  content     VARCHAR(1000) NOT NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_feedback_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
  CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  INDEX idx_feedback_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS check_in (
  id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  activity_id    BIGINT      NOT NULL,
  user_id        VARCHAR(32) NOT NULL,
  method         VARCHAR(16) NOT NULL COMMENT 'qrcode/location/password',
  check_in_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_checkin_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
  CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  UNIQUE KEY uk_checkin_activity_user (activity_id, user_id),
  INDEX idx_checkin_activity (activity_id),
  INDEX idx_checkin_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity_analysis (
  id                     BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  activity_id            BIGINT      NOT NULL,
  signup_rate            DECIMAL(5,1) NOT NULL,
  attendance_rate        DECIMAL(5,1) NOT NULL,
  avg_rating             DECIMAL(3,2) NULL,
  favorite_conversion    DECIMAL(5,1) NULL,
  rating_distribution    JSON NULL,
  check_in_methods_stats JSON NULL,
  metrics_json           JSON NULL,
  suggestions            JSON NULL,
  suggestion_source      VARCHAR(16) NOT NULL DEFAULT 'llm',
  suggestion_model       VARCHAR(64) NULL,
  generated_at           DATETIME(3) NOT NULL,
  created_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_analysis_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
  UNIQUE KEY uk_analysis_activity (activity_id),
  INDEX idx_analysis_generated (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS analysis_schedule_log (
  id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  job_name       VARCHAR(64)   NOT NULL,
  activity_id    BIGINT        NULL,
  status         VARCHAR(16)   NOT NULL,
  duration_ms    INT           NULL,
  error_message  VARCHAR(1000) NULL,
  started_at     DATETIME(3)   NOT NULL,
  finished_at    DATETIME(3)   NULL,
  INDEX idx_log_job (job_name, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
