SET NAMES utf8mb4;

USE campus_activity;

ALTER TABLE `user`
  ADD COLUMN jaccount VARCHAR(64) NULL COMMENT 'jAccount 唯一标识 sub' AFTER role,
  ADD COLUMN jaccount_type VARCHAR(32) NULL COMMENT 'jAccount 身份类别' AFTER jaccount,
  ADD UNIQUE KEY uk_user_jaccount (jaccount);
