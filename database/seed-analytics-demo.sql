-- 智能分析模块演示数据。
-- 可重复执行：只重建签到码为 AI-DEMO 的活动，不影响其他数据。
SET NAMES utf8mb4;
START TRANSACTION;

SET @demo_title = _utf8mb4'智能分析演示：校园 AI 实践工作坊' COLLATE utf8mb4_unicode_ci;
SET @old_activity_id = (SELECT id FROM activity WHERE check_in_code = 'AI-DEMO' ORDER BY id LIMIT 1);
DELETE FROM activity WHERE id = @old_activity_id;

INSERT INTO activity (
  title, category, description, start_time, end_time, location,
  organizer_id, college, max_participants, signup_count, view_count,
  favorite_count, status, tags, check_in_code
) VALUES (
  @demo_title,
  'academic',
  '用于验证报名转化率、到场率、评分分布、文字反馈脱敏及改进建议生成。',
  DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY) + INTERVAL 14 HOUR,
  DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY) + INTERVAL 17 HOUR,
  '软件大楼 B203',
  'T001',
  '电子信息与电气工程学院',
  30, 7, 20, 10, 'ended',
  JSON_ARRAY('AI', '实践', '数据分析'),
  'AI-DEMO'
);
SET @activity_id = LAST_INSERT_ID();

INSERT INTO registration (activity_id, user_id, status, created_at) VALUES
(@activity_id, '524030910001', 'approved', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@activity_id, '524030910002', 'approved', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@activity_id, 'S001',         'approved', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@activity_id, 'S002',         'approved', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@activity_id, 'S003',         'approved', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(@activity_id, 'S004',         'approved', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(@activity_id, 'admin001',     'approved', DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO check_in (activity_id, user_id, method, check_in_time) VALUES
(@activity_id, '524030910001', 'qrcode',  DATE_SUB(NOW(), INTERVAL 1 DAY)),
(@activity_id, '524030910002', 'qrcode',  DATE_SUB(NOW(), INTERVAL 1 DAY)),
(@activity_id, 'S001',         'location',DATE_SUB(NOW(), INTERVAL 1 DAY)),
(@activity_id, 'S002',         'password',DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO feedback (activity_id, user_id, rating, content, created_at) VALUES
(@activity_id, '524030910001', 4, '案例很实用，但后排投影偏暗，代码看不清。', NOW()),
(@activity_id, '524030910002', 3, '签到排队接近十五分钟，建议增加二维码签到点。', NOW()),
(@activity_id, 'S001',         2, '活动安排在午后，与实验课冲突，迟到了半小时。', NOW()),
(@activity_id, 'S002',         3, '讲解节奏偏快，希望增加动手练习和助教答疑。', NOW()),
(@activity_id, 'S003',         2, '教室座位和插座不足，电脑中途没电，体验一般。', NOW());

COMMIT;

SELECT id, title, view_count, signup_count, favorite_count
FROM activity WHERE id = @activity_id;
