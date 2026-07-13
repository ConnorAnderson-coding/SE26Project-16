DELETE FROM feedback WHERE activity_id>=10;
DELETE FROM check_in WHERE activity_id>=10;
DELETE FROM registration WHERE activity_id>=10;
DELETE FROM activity_analysis WHERE activity_id>=10;
DELETE FROM activity WHERE id>=10;

SET NAMES utf8mb4;

INSERT INTO activity (title, category, description, start_time, end_time, location, organizer_id, college, max_participants, signup_count, favorite_count, status, view_count) VALUES
('Python 数据分析实战工作坊', 'academic', '面向零基础同学的 Python 数据分析入门课程', '2026-07-08 14:00:00', '2026-07-08 17:00:00', '软件大楼 B203', '524030910002', '电子信息与电气工程学院', 80, 82, 95, 'ended', 2840);

INSERT INTO activity (title, category, description, start_time, end_time, location, organizer_id, college, max_participants, signup_count, favorite_count, status, view_count) VALUES
('春日校园摄影大赛', 'culture', '用镜头记录校园春色，优秀作品将在图书馆大厅展出', '2026-07-06 09:00:00', '2026-07-06 18:00:00', '思源湖畔集合', 'T001', '媒体与传播学院', 100, 68, 120, 'ended', 5100);

INSERT INTO activity (title, category, description, start_time, end_time, location, organizer_id, college, max_participants, signup_count, favorite_count, status, view_count) VALUES
('期末复习冲刺夜', 'academic', '学院组织期末集中答疑辅导，学霸助教现场辅导', '2026-07-09 19:00:00', '2026-07-09 23:00:00', '图书馆 B区自习室', '524030910001', '数学科学学院', 200, 187, 45, 'ended', 1650);
