USE campus_activity;

-- Password for all demo users: 123456
-- BCrypt hash generated with cost factor 10

INSERT INTO `user` (id, password_hash, name, role, college, grade, interests, available_time) VALUES
('524030910001', '$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci', '张三', 'student', '软件学院', '2024级', '["AI","摄影","羽毛球"]', '["weekday_evening","weekend"]'),
('524030910002', '$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci', '李四', 'student', '计算机学院', '2023级', '["编程","电竞","篮球"]', '["weekend"]'),
('T001', '$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci', '王老师', 'teacher', '软件学院', '教师', '["AI","创业"]', '["weekday_morning","weekday_afternoon"]'),
('admin001', '$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci', '系统管理员', 'admin', '软件学院', '管理员', '[]', '[]');

INSERT INTO activity (id, title, category, description, start_time, end_time, location, organizer_id, college, poster, max_participants, signup_count, favorite_count, status, tags, check_in_code) VALUES
(1, 'AI 与大模型技术前沿讲座', 'academic', '本次讲座邀请业界专家，介绍大模型在软件工程、教育等领域的最新应用与发展趋势，适合对 AI 感兴趣的同学参加。', '2026-07-15 14:00:00.000', '2026-07-15 16:00:00.000', '软件大楼 A101', 'T001', '软件学院', 'https://picsum.photos/seed/ai-lecture/800/400', 120, 85, 42, 'published', '["AI","编程"]', 'AI2026'),
(2, '校园羽毛球友谊赛', 'sports', '面向全校师生的羽毛球双打友谊赛，按学院分组，优胜队伍将获得精美奖品。请提前热身，穿运动服参赛。', '2026-07-20 09:00:00.000', '2026-07-20 12:00:00.000', '体育馆羽毛球场', '524030910002', '计算机学院', 'https://picsum.photos/seed/badminton/800/400', 64, 43, 28, 'published', '["羽毛球","体育运动"]', 'BD2026'),
(3, '摄影社户外采风活动', 'club', '摄影社组织校园及周边人文采风，专业学长带队讲解构图与后期技巧，欢迎零基础同学加入。', '2026-07-18 08:00:00.000', '2026-07-18 17:00:00.000', '图书馆前广场集合', '524030910001', '软件学院', 'https://picsum.photos/seed/photo-club/800/400', 30, 22, 35, 'published', '["摄影","艺术"]', 'PH2026'),
(4, '程序设计竞赛训练营', 'innovation', '为期一周的算法与数据结构强化训练，涵盖动态规划、图论等高频考点，为 ACM/ICPC 及各类编程竞赛做准备。', '2026-07-22 19:00:00.000', '2026-07-29 21:00:00.000', '计算机楼 302 实验室', 'T001', '计算机学院', 'https://picsum.photos/seed/coding/800/400', 50, 38, 56, 'published', '["编程","AI"]', 'CP2026'),
(5, '校园志愿者招募 — 社区服务日', 'volunteer', '组织同学前往周边社区开展助老、环境清洁等志愿服务，可计入志愿服务时长，欢迎热心公益的同学报名。', '2026-07-25 08:30:00.000', '2026-07-25 16:00:00.000', '校门口集合', '524030910001', '软件学院', 'https://picsum.photos/seed/volunteer/800/400', 40, 31, 19, 'published', '["志愿服务"]', 'VL2026'),
(6, '校园音乐节 — 夏日之声', 'arts', '各社团及个人歌手同台演出，涵盖流行、摇滚、民谣等多种风格，现场还有互动抽奖环节。', '2026-06-28 18:30:00.000', '2026-06-28 21:30:00.000', '中心广场', '524030910002', '信息学院', 'https://picsum.photos/seed/music/800/400', 500, 412, 198, 'ended', '["音乐","文艺"]', 'MU2026');

INSERT INTO registration (id, activity_id, user_id, status, created_at) VALUES
(1, 1, '524030910001', 'approved', '2026-07-01 10:00:00.000'),
(2, 2, '524030910001', 'pending', '2026-07-02 14:00:00.000'),
(3, 3, '524030910002', 'approved', '2026-07-03 09:00:00.000'),
(4, 1, '524030910002', 'pending', '2026-07-04 11:00:00.000'),
(5, 3, '524030910001', 'approved', '2026-07-05 08:00:00.000'),
(6, 3, 'T001', 'approved', '2026-07-05 09:00:00.000'),
(7, 5, '524030910002', 'approved', '2026-07-10 10:00:00.000'),
(8, 5, '524030910001', 'approved', '2026-07-11 11:00:00.000'),
(9, 6, '524030910002', 'approved', '2026-06-20 10:00:00.000'),
(10, 4, '524030910001', 'approved', '2026-07-12 14:00:00.000'),
(11, 4, '524030910002', 'pending', '2026-07-13 09:00:00.000');

INSERT INTO favorite (user_id, activity_id) VALUES
('524030910001', 1),
('524030910001', 4),
('524030910002', 2);

INSERT INTO activity_record (activity_id, summary, photos, published_at) VALUES
(5, '本次活动共有 31 名志愿者参与，服务时长累计 248 小时，获得社区居民一致好评。', '["https://picsum.photos/seed/vol1/400/300","https://picsum.photos/seed/vol2/400/300","https://picsum.photos/seed/vol3/400/300"]', '2026-07-26 10:00:00.000'),
(6, '音乐节圆满落幕，12 支乐队和 8 位独立歌手带来精彩演出，现场观众超过 800 人。', '["https://picsum.photos/seed/music1/400/300","https://picsum.photos/seed/music2/400/300"]', '2026-06-29 09:00:00.000');

INSERT INTO feedback (id, activity_id, user_id, rating, content, created_at) VALUES
(1, 6, '524030910001', 5, '演出非常精彩，氛围很好，希望每年都能举办！', '2026-06-29 12:00:00.000'),
(2, 6, '524030910002', 4, '现场音响效果不错，建议明年增加互动环节。', '2026-06-29 14:00:00.000'),
(3, 3, '524030910002', 5, '采风活动组织得很好，学到了很多摄影技巧。', '2026-07-19 10:00:00.000');
