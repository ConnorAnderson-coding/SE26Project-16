-- 补充分析模块样例数据
-- 包含：浏览量、签到记录、评分和文字反馈

SET NAMES utf8mb4;
USE campus_activity;

-- ========== 1. 为已有活动补充浏览量，便于统计报名转化率 ==========

UPDATE activity SET view_count = CASE id
  WHEN 1 THEN 320
  WHEN 2 THEN 180
  WHEN 3 THEN 150
  WHEN 4 THEN 260
  WHEN 5 THEN 220
  WHEN 6 THEN 4800
END WHERE id IN (1,2,3,4,5,6);

-- ========== 2. 补充签到记录（3 种方式）==========

TRUNCATE TABLE check_in;

INSERT INTO check_in (activity_id, user_id, method, check_in_time) VALUES
-- Activity 1: AI讲座 (3个签到)
(1, '524030910001', 'qrcode', '2026-07-15 15:05:00'),
(1, '524030910002', 'location', '2026-07-15 15:10:00'),
(1, 'T001', 'password', '2026-07-15 15:20:00'),
-- Activity 2: 羽毛球赛 (2个签到)
(2, '524030910001', 'qrcode', '2026-07-20 09:30:00'),
(2, '524030910002', 'location', '2026-07-20 09:45:00'),
-- Activity 3: 摄影采风 (2个签到)
(3, '524030910002', 'qrcode', '2026-07-18 09:00:00'),
(3, '524030910001', 'password', '2026-07-18 09:30:00'),
-- Activity 4: 编程训练营 (2个签到)
(4, '524030910001', 'location', '2026-07-22 19:30:00'),
(4, '524030910002', 'qrcode', '2026-07-22 20:00:00'),
-- Activity 5: 志愿服务 (2个签到)
(5, '524030910002', 'qrcode', '2026-07-25 08:40:00'),
(5, '524030910001', 'password', '2026-07-25 09:00:00'),
-- Activity 6: 校园音乐节 (3个签到)
(6, '524030910001', 'qrcode', '2026-06-28 18:50:00'),
(6, '524030910002', 'location', '2026-06-28 19:15:00'),
(6, 'T001', 'password', '2026-06-28 20:00:00');

-- ========== 3. 补充评分和文字反馈 ==========
-- 需要满足指标维度：浏览量、报名转化率、到场率、平均评分、评分分布、场地设施、时间安排、内容质量

TRUNCATE TABLE feedback;

INSERT INTO feedback (activity_id, user_id, rating, content, created_at) VALUES
-- Activity 1: AI讲座 (3条反馈，覆盖场地、内容、时间)
(1, '524030910001', 5, '讲座内容非常实用，讲师表达清晰，建议多增加互动环节。', '2026-07-16 10:00:00'),
(1, '524030910002', 4, '整体不错，时间安排比较合理，能更深入讲一点实践案例。投影清晰度一般。', '2026-07-16 11:30:00'),
(1, 'T001', 3, '内容有价值，但场地音响略有改善空间，后排体验不太理想。', '2026-07-16 12:00:00'),

-- Activity 2: 羽毛球赛 (2条反馈，覆盖场地、时间)
(2, '524030910001', 5, '比赛组织很顺利，现场氛围很好，场地设施完善。', '2026-07-21 09:00:00'),
(2, '524030910002', 4, '整体不错，场地排队等待时间有点久，建议优化签到流程。', '2026-07-21 10:30:00'),

-- Activity 3: 摄影采风 (2条反馈，覆盖内容、时间)
(3, '524030910002', 5, '采风活动组织得很好，学到了很多摄影技巧，路线设计科学合理。', '2026-07-19 10:00:00'),
(3, '524030910001', 4, '整体体验不错，建议在事前发送详细的集合时间和路线说明。', '2026-07-19 11:00:00'),

-- Activity 4: 编程训练营 (2条反馈，覆盖内容、时间)
(4, '524030910001', 2, '训练内容略重，节奏可以更平缓一些，每天学习时间太长了。', '2026-07-24 20:00:00'),
(4, '524030910002', 4, '讲解非常清楚，练习题设计有帮助，建议增加项目实战环节。', '2026-07-24 21:00:00'),

-- Activity 5: 志愿服务 (2条反馈，覆盖场地、内容)
(5, '524030910002', 5, '志愿服务很有意义，组织也很到位，工作分配明确。', '2026-07-26 10:30:00'),
(5, '524030910001', 4, '流程顺畅，期待下次再参加，建议提前一周发送参与须知。', '2026-07-26 11:00:00'),

-- Activity 6: 校园音乐节 (3条反馈，覆盖场地、内容、宣传)
(6, '524030910001', 5, '演出非常精彩，氛围很好，舞台灯光和音响效果一流，希望每年都能举办。', '2026-06-29 12:00:00'),
(6, '524030910002', 4, '现场音响效果不错，演员阵容强大，建议明年增加更多现场互动环节。', '2026-06-29 14:00:00'),
(6, 'T001', 3, '整体不错，投影屏幕分辨率有点低，后排的观众看不太清楚舞台细节。', '2026-06-29 18:00:00');

-- ========== 4. 验证样例数据 ==========

SELECT '=== 活动与浏览量 ===' AS label;
SELECT id, title, view_count, signup_count, ROUND(signup_count*100.0/view_count,1) AS signup_rate_pct FROM activity;

SELECT '=== 签到统计 ===' AS label;
SELECT a.id, COUNT(c.id) AS checkin_count FROM activity a LEFT JOIN check_in c ON a.id = c.activity_id GROUP BY a.id;

SELECT '=== 评分统计 ===' AS label;
SELECT activity_id, COUNT(*) AS feedback_count, ROUND(AVG(rating),2) AS avg_rating FROM feedback GROUP BY activity_id;

SELECT '=== 到场率 ===' AS label;
SELECT 
  a.id,
  COUNT(DISTINCT r.user_id) AS signup_total,
  COUNT(DISTINCT c.user_id) AS checkin_total,
  ROUND(COUNT(DISTINCT c.user_id)*100.0/COUNT(DISTINCT r.user_id),1) AS attendance_rate_pct
FROM activity a 
LEFT JOIN registration r ON a.id = r.activity_id AND r.status = 'approved'
LEFT JOIN check_in c ON a.id = c.activity_id
GROUP BY a.id;
