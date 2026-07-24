-- 修复 feedback.rating 类型：TINYINT -> INT（与 JPA Integer 对齐）
USE campus_activity;

ALTER TABLE feedback MODIFY rating INT NOT NULL;
