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
  jaccount        VARCHAR(64)  NULL COMMENT 'jAccount 唯一标识 sub',
  jaccount_type   VARCHAR(32)  NULL COMMENT 'jAccount 身份类别',
  college         VARCHAR(64)  NOT NULL,
  grade           VARCHAR(32)  NOT NULL,
  interests       JSON         NULL COMMENT '兴趣标签数组',
  available_time  JSON         NULL COMMENT '可参与时间数组',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user_jaccount (jaccount),
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
  check_in_count   INT          NOT NULL DEFAULT 0,
  hotness_score    DOUBLE       NOT NULL DEFAULT 0,
  status           VARCHAR(16)  NOT NULL DEFAULT 'published' COMMENT 'draft/published/ended',
  tags             JSON         NULL,
  check_in_code    VARCHAR(32)  NULL,
  latitude         DOUBLE       NULL,
  longitude        DOUBLE       NULL,
  check_in_radius_m INT         NOT NULL DEFAULT 200,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_activity_organizer FOREIGN KEY (organizer_id) REFERENCES `user`(id),
  INDEX idx_activity_status (status),
  INDEX idx_activity_category (category),
  INDEX idx_activity_start_time (start_time),
  INDEX idx_activity_organizer (organizer_id),
  INDEX idx_activity_hotness (hotness_score)
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

CREATE TABLE IF NOT EXISTS activity_analysis (
  id                       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  activity_id              BIGINT       NOT NULL,
  signup_rate              DECIMAL(5,1) NOT NULL,
  attendance_rate          DECIMAL(5,1) NOT NULL,
  avg_rating               DECIMAL(3,2) NULL,
  rating_distribution      JSON NULL,
  check_in_methods_stats   JSON NULL,
  suggestions              JSON NULL,
  suggestion_source        VARCHAR(16)  NOT NULL DEFAULT 'llm',
  suggestion_model         VARCHAR(64)  NULL,
  analysis_status          VARCHAR(16)  NOT NULL DEFAULT 'ready' COMMENT 'pending/ready/failed',
  failure_reason           VARCHAR(500) NULL,
  generated_at             DATETIME(3)  NOT NULL,
  created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  -- 一次性快照字段：加入分析列表时一次性读取，之后冻结不随 activity 行变化
  view_count_snapshot      INT          NULL COMMENT '冻结时的浏览量',
  signup_count_snapshot    INT          NULL COMMENT '冻结时的报名量',
  favorite_count_snapshot  INT          NULL COMMENT '冻结时的收藏量',
  snapshot_at              DATETIME(3)  NULL COMMENT '快照生成时间',
  CONSTRAINT fk_analysis_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
  UNIQUE KEY uk_analysis_activity (activity_id),
  INDEX idx_analysis_generated (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
