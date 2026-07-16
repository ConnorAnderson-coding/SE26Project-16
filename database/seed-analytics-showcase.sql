

SET NAMES utf8mb4;
USE campus_activity;

START TRANSACTION;

DELETE FROM activity
WHERE check_in_code LIKE 'ANALYTICS-SHOW-%';

INSERT INTO `user` (id, password_hash, name, role, college, grade, interests, available_time)
VALUES
('D001', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生01', 'student', '电子信息与电气工程学院', '2024', JSON_ARRAY('AI', '讲座'), JSON_ARRAY('weekday_evening')),
('D002', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生02', 'student', '电子信息与电气工程学院', '2024', JSON_ARRAY('AI', '实践'), JSON_ARRAY('weekend')),
('D003', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生03', 'student', '电子信息与电气工程学院', '2023', JSON_ARRAY('志愿', '公益'), JSON_ARRAY('weekday_noon')),
('D004', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生04', 'student', '电子信息与电气工程学院', '2023', JSON_ARRAY('竞赛'), JSON_ARRAY('weekday_evening')),
('D005', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生05', 'student', '软件学院', '2024', JSON_ARRAY('就业', '分享'), JSON_ARRAY('weekend')),
('D006', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生06', 'student', '软件学院', '2024', JSON_ARRAY('AI'), JSON_ARRAY('weekday_evening')),
('D007', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生07', 'student', '软件学院', '2023', JSON_ARRAY('讲座'), JSON_ARRAY('weekend')),
('D008', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生08', 'student', '软件学院', '2023', JSON_ARRAY('实践'), JSON_ARRAY('weekday_noon')),
('D009', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生09', 'student', '管理学院', '2024', JSON_ARRAY('社团'), JSON_ARRAY('weekday_evening')),
('D010', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生10', 'student', '管理学院', '2024', JSON_ARRAY('创业'), JSON_ARRAY('weekend')),
('D011', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生11', 'student', '管理学院', '2023', JSON_ARRAY('志愿'), JSON_ARRAY('weekday_noon')),
('D012', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生12', 'student', '管理学院', '2023', JSON_ARRAY('竞赛'), JSON_ARRAY('weekday_evening')),
('D013', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生13', 'student', '设计学院', '2024', JSON_ARRAY('设计'), JSON_ARRAY('weekend')),
('D014', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生14', 'student', '设计学院', '2024', JSON_ARRAY('艺术'), JSON_ARRAY('weekday_evening')),
('D015', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生15', 'student', '设计学院', '2023', JSON_ARRAY('分享'), JSON_ARRAY('weekday_noon')),
('D016', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生16', 'student', '设计学院', '2023', JSON_ARRAY('实践'), JSON_ARRAY('weekend')),
('D017', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生17', 'student', '电子信息与电气工程学院', '2022', JSON_ARRAY('AI'), JSON_ARRAY('weekday_evening')),
('D018', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生18', 'student', '软件学院', '2022', JSON_ARRAY('就业'), JSON_ARRAY('weekend')),
('D019', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生19', 'student', '管理学院', '2022', JSON_ARRAY('创业'), JSON_ARRAY('weekday_noon')),
('D020', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', '演示学生20', 'student', '设计学院', '2022', JSON_ARRAY('设计'), JSON_ARRAY('weekday_evening'))
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role = VALUES(role),
  college = VALUES(college),
  grade = VALUES(grade),
  interests = VALUES(interests),
  available_time = VALUES(available_time);

INSERT INTO activity (
  title, category, description, start_time, end_time, location,
  organizer_id, college, max_participants, signup_count, view_count,
  favorite_count, status, tags, check_in_code
) VALUES (
  '分析演示A：AI工具入门午间课',
  'academic',
  '浏览量高但报名转化偏低，用于观察宣传推广类建议。',
  DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY) + INTERVAL 12 HOUR,
  DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY) + INTERVAL 14 HOUR,
  '软件楼 B203',
  'T001',
  '电子信息与电气工程学院',
  60, 12, 120, 24, 'ended',
  JSON_ARRAY('AI', '午间课', '转化率低'),
  'ANALYTICS-SHOW-PROMO'
);
SET @a_promo = LAST_INSERT_ID();

INSERT INTO activity (
  title, category, description, start_time, end_time, location,
  organizer_id, college, max_participants, signup_count, view_count,
  favorite_count, status, tags, check_in_code
) VALUES (
  '分析演示B：周五晚就业分享会',
  'academic',
  '报名热度不错但到场不足，用于观察时间安排和提醒机制建议。',
  DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY) + INTERVAL 19 HOUR,
  DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY) + INTERVAL 21 HOUR,
  '教学楼 C101',
  'T001',
  '软件学院',
  40, 27, 55, 18, 'ended',
  JSON_ARRAY('就业', '分享', '到场率低'),
  'ANALYTICS-SHOW-ATTEND'
);
SET @a_attend = LAST_INSERT_ID();

INSERT INTO activity (
  title, category, description, start_time, end_time, location,
  organizer_id, college, max_participants, signup_count, view_count,
  favorite_count, status, tags, check_in_code
) VALUES (
  '分析演示C：创新创业路演训练营',
  'competition',
  '报名和到场尚可，但用户集中反馈场地设备问题。',
  DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY) + INTERVAL 15 HOUR,
  DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY) + INTERVAL 17 HOUR,
  '实验楼 A506',
  'T001',
  '管理学院',
  35, 22, 60, 15, 'ended',
  JSON_ARRAY('创业', '路演', '场地反馈'),
  'ANALYTICS-SHOW-VENUE'
);
SET @a_venue = LAST_INSERT_ID();

INSERT INTO activity (
  title, category, description, start_time, end_time, location,
  organizer_id, college, max_participants, signup_count, view_count,
  favorite_count, status, tags, check_in_code
) VALUES (
  '分析演示D：数据可视化实战工作坊',
  'academic',
  '报名和到场都不错，但评分偏低，用于观察内容质量建议。',
  DATE_SUB(CURRENT_DATE, INTERVAL 4 DAY) + INTERVAL 14 HOUR,
  DATE_SUB(CURRENT_DATE, INTERVAL 4 DAY) + INTERVAL 17 HOUR,
  '机房 D302',
  'T001',
  '软件学院',
  45, 28, 70, 22, 'ended',
  JSON_ARRAY('数据分析', '可视化', '内容反馈'),
  'ANALYTICS-SHOW-CONTENT'
);
SET @a_content = LAST_INSERT_ID();

INSERT INTO activity (
  title, category, description, start_time, end_time, location,
  organizer_id, college, max_participants, signup_count, view_count,
  favorite_count, status, tags, check_in_code
) VALUES (
  '分析演示E：校园志愿服务开放日',
  'volunteer',
  '各项指标表现较好，用于观察整体保持和微优化建议。',
  DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY) + INTERVAL 9 HOUR,
  DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY) + INTERVAL 11 HOUR,
  '大学生活动中心一楼',
  'T001',
  '校团委',
  50, 28, 48, 30, 'ended',
  JSON_ARRAY('志愿服务', '开放日', '表现优秀'),
  'ANALYTICS-SHOW-GOOD'
);
SET @a_good = LAST_INSERT_ID();

INSERT INTO registration (activity_id, user_id, status, created_at) VALUES
(@a_promo, 'D001', 'approved', DATE_SUB(NOW(), INTERVAL 12 DAY)),
(@a_promo, 'D002', 'approved', DATE_SUB(NOW(), INTERVAL 11 DAY)),
(@a_promo, 'D003', 'approved', DATE_SUB(NOW(), INTERVAL 11 DAY)),
(@a_promo, 'D004', 'approved', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(@a_promo, 'D005', 'approved', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(@a_promo, 'D006', 'approved', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(@a_promo, 'D007', 'approved', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(@a_promo, 'D008', 'approved', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(@a_promo, 'D009', 'approved', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(@a_promo, 'D010', 'approved', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(@a_promo, 'D011', 'approved', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D012', 'approved', DATE_SUB(NOW(), INTERVAL 7 DAY)),

(@a_attend, 'D001', 'approved', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(@a_attend, 'D002', 'approved', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(@a_attend, 'D003', 'approved', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(@a_attend, 'D004', 'approved', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(@a_attend, 'D005', 'approved', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(@a_attend, 'D006', 'approved', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(@a_attend, 'D007', 'approved', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(@a_attend, 'D008', 'approved', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(@a_attend, 'D009', 'approved', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(@a_attend, 'D010', 'approved', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(@a_attend, 'D011', 'approved', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_attend, 'D012', 'approved', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_attend, 'D013', 'approved', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_attend, 'D014', 'approved', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_attend, 'D015', 'approved', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_attend, 'D016', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D017', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D018', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D019', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D020', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, '524030910001', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, '524030910002', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'S001', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'S002', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'S003', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'S004', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'admin001', 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY))
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO registration (activity_id, user_id, status, created_at)
SELECT @a_venue, id, 'approved', DATE_SUB(NOW(), INTERVAL 7 DAY)
FROM `user`
WHERE id IN ('D001','D002','D003','D004','D005','D006','D007','D008','D009','D010','D011','D012','D013','D014','D015','D016','D017','D018','D019','D020','S001','S002');

INSERT INTO registration (activity_id, user_id, status, created_at)
SELECT @a_content, id, 'approved', DATE_SUB(NOW(), INTERVAL 6 DAY)
FROM `user`
WHERE id IN ('D001','D002','D003','D004','D005','D006','D007','D008','D009','D010','D011','D012','D013','D014','D015','D016','D017','D018','D019','D020','S001','S002','S003','S004','524030910001','524030910002','admin001','T001','D001','D002','D003','D004','D005');

INSERT INTO registration (activity_id, user_id, status, created_at)
SELECT @a_good, id, 'approved', DATE_SUB(NOW(), INTERVAL 5 DAY)
FROM `user`
WHERE id IN ('D001','D002','D003','D004','D005','D006','D007','D008','D009','D010','D011','D012','D013','D014','D015','D016','D017','D018','D019','D020','S001','S002','S003','S004','524030910001','524030910002','admin001','T001','D001','D002','D003','D004','D005','D006','D007','D008');

INSERT INTO check_in (activity_id, user_id, method, check_in_time) VALUES
(@a_promo, 'D001', 'qrcode', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D002', 'qrcode', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D003', 'location', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D004', 'password', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D005', 'qrcode', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D006', 'qrcode', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D007', 'location', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D008', 'qrcode', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D009', 'password', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@a_promo, 'D010', 'qrcode', DATE_SUB(NOW(), INTERVAL 7 DAY)),

(@a_attend, 'D001', 'qrcode', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D002', 'qrcode', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D003', 'qrcode', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D004', 'location', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D005', 'password', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D006', 'qrcode', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D007', 'qrcode', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@a_attend, 'D008', 'location', DATE_SUB(NOW(), INTERVAL 6 DAY)),

(@a_venue, 'D001', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D002', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D003', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D004', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D005', 'location', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D006', 'location', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D007', 'password', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D008', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D009', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D010', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D011', 'location', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D012', 'password', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D013', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D014', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D015', 'location', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@a_venue, 'D016', 'qrcode', DATE_SUB(NOW(), INTERVAL 5 DAY)),

(@a_content, 'D001', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D002', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D003', 'location', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D004', 'password', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D005', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D006', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D007', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D008', 'location', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D009', 'password', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D010', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D011', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D012', 'location', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D013', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D014', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D015', 'password', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D016', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D017', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D018', 'location', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D019', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'D020', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'S001', 'password', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'S002', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'S003', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, 'S004', 'location', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, '524030910001', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@a_content, '524030910002', 'qrcode', DATE_SUB(NOW(), INTERVAL 4 DAY)),

(@a_good, 'D001', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D002', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D003', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D004', 'location', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D005', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D006', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D007', 'password', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D008', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D009', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D010', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D011', 'location', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D012', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D013', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D014', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D015', 'password', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D016', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D017', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D018', 'location', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D019', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'D020', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'S001', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'S002', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'S003', 'location', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'S004', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, '524030910001', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, '524030910002', 'password', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'admin001', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@a_good, 'T001', 'qrcode', DATE_SUB(NOW(), INTERVAL 3 DAY));

INSERT INTO feedback (activity_id, user_id, rating, content, created_at) VALUES
(@a_promo, 'D001', 5, '内容其实不错，但我是在同学转发后才看到，活动标题没有体现能学到什么。', NOW()),
(@a_promo, 'D002', 4, '午间时间方便，建议海报上写清楚适合零基础同学参加。', NOW()),
(@a_promo, 'D003', 4, '讲解清楚，但宣传渠道太少，很多同学活动结束才知道。', NOW()),
(@a_promo, 'D004', 5, '案例实用，希望下次提前几天发布材料。', NOW()),

(@a_attend, 'D001', 4, '嘉宾分享不错，但周五晚上很多人临时有安排，身边不少同学报名后没来。', NOW()),
(@a_attend, 'D002', 3, '活动前提醒不够明显，我差点忘记时间。', NOW()),
(@a_attend, 'D003', 3, '签到排队有点久，建议多放一个二维码签到点。', NOW()),
(@a_attend, 'D004', 4, '内容有价值，但结束时间偏晚，后半场人明显变少。', NOW()),

(@a_venue, 'D001', 2, '投影不清楚，后排基本看不清路演模板上的小字。', NOW()),
(@a_venue, 'D002', 3, '音响有电流声，提问环节听不太清楚。', NOW()),
(@a_venue, 'D003', 2, '教室座位太挤，插座也不够，电脑中途没电。', NOW()),
(@a_venue, 'D004', 3, '内容还可以，但场地灯光太暗，拍摄路演视频效果一般。', NOW()),
(@a_venue, 'D005', 4, '老师点评很具体，如果设备更稳定会更好。', NOW()),

(@a_content, 'D001', 2, '节奏太快，前面概念还没理解就开始做图表。', NOW()),
(@a_content, 'D002', 3, '案例偏难，建议先给一份入门数据集和步骤说明。', NOW()),
(@a_content, 'D003', 2, '互动练习时间太短，很多同学没跟上。', NOW()),
(@a_content, 'D004', 3, '讲师很认真，但内容跨度大，希望拆成基础和进阶两场。', NOW()),
(@a_content, 'D005', 4, '工具演示有用，如果课后能给录屏和模板会更好。', NOW()),

(@a_good, 'D001', 5, '流程顺畅，签到很快，志愿岗位介绍也很清楚。', NOW()),
(@a_good, 'D002', 5, '现场动线好，工作人员引导及时，体验很好。', NOW()),
(@a_good, 'D003', 4, '内容完整，建议下次增加更多真实服务案例。', NOW()),
(@a_good, 'D004', 5, '时间安排合理，结束后还有答疑，愿意继续参加。', NOW()),
(@a_good, 'D005', 5, '宣传清楚，地点好找，整体体验很棒。', NOW());

COMMIT;

SELECT
  id,
  check_in_code,
  title,
  view_count,
  signup_count,
  ROUND(signup_count * 100 / NULLIF(view_count, 0), 1) AS signup_rate
FROM activity
WHERE check_in_code LIKE 'ANALYTICS-SHOW-%'
ORDER BY check_in_code;
