SET NAMES utf8mb4;
INSERT INTO feedback (activity_id, user_id, rating, content) VALUES
(5,'524030910001',5,'组织非常有序，志愿者分工明确，希望以后多举办！'),
(5,'524030910002',4,'签到排队太久，等了15分钟，建议增加签到点。'),
(5,'T001',3,'集合地点指示不清晰，校门口找了很久。'),
(5,'S001',5,'非常棒的体验！认识了很多朋友。'),
(5,'S002',4,'活动时间有点短，还没尽兴就结束了。');
SELECT COUNT(*) AS total FROM feedback;
