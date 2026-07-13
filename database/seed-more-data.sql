SET NAMES utf8mb4;

-- 报名
INSERT INTO registration (activity_id, user_id, status) VALUES
(13,'524030910001','approved'),(13,'524030910002','approved'),(13,'T001','approved'),
(13,'S001','approved'),(13,'S002','approved'),(13,'S003','approved'),
(13,'S004','approved'),(13,'S005','approved'),(13,'S006','approved');

INSERT INTO registration (activity_id, user_id, status) VALUES
(14,'524030910001','approved'),(14,'524030910002','approved'),(14,'T001','approved'),
(14,'S001','approved'),(14,'S002','approved'),(14,'S003','approved'),
(14,'S004','approved'),(14,'S005','approved'),(14,'S006','approved');

INSERT INTO registration (activity_id, user_id, status) VALUES
(15,'524030910001','approved'),(15,'524030910002','approved'),(15,'T001','approved'),
(15,'S001','approved'),(15,'S002','approved'),(15,'S003','approved'),
(15,'S004','approved'),(15,'S005','approved'),(15,'S006','approved'),
(15,'S007','approved'),(15,'S008','approved'),(15,'S009','approved');

-- 签到
INSERT INTO check_in (activity_id, user_id, method) VALUES
(13,'524030910001','qrcode'),(13,'524030910002','qrcode'),(13,'T001','qrcode'),
(13,'S001','qrcode'),(13,'S002','qrcode'),(13,'S003','location'),(13,'S004','qrcode');

INSERT INTO check_in (activity_id, user_id, method) VALUES
(14,'524030910001','qrcode'),(14,'524030910002','qrcode'),(14,'T001','qrcode');

INSERT INTO check_in (activity_id, user_id, method) VALUES
(15,'524030910001','qrcode'),(15,'524030910002','qrcode'),(15,'T001','qrcode'),
(15,'S001','qrcode'),(15,'S002','qrcode'),(15,'S003','qrcode'),
(15,'S004','qrcode'),(15,'S005','qrcode'),(15,'S006','qrcode'),
(15,'S007','qrcode'),(15,'S008','location');

-- 评价
INSERT INTO feedback (activity_id, user_id, rating, content) VALUES
(13,'524030910001',5,'讲师讲得很清楚，零基础也能跟上！'),
(13,'524030910002',5,'收获很大，希望能开进阶课'),
(13,'T001',4,'内容充实，但时间有点紧'),
(13,'S001',5,'非常棒的课程体验'),
(13,'S002',4,'实战案例很实用'),
(13,'S003',5,'助教很耐心，手把手教'),
(13,'S004',3,'后排投影看不清，建议换大屏幕教室');

INSERT INTO feedback (activity_id, user_id, rating, content) VALUES
(14,'524030910001',3,'照片评选标准不透明，感觉有点随意'),
(14,'524030910002',2,'到了现场发现人很少，组织者没准备充分的签到流程'),
(14,'T001',4,'拍摄主题很好，但活动宣传不够'),
(14,'S001',3,'奖品不错，但现场秩序有点乱'),
(14,'S002',2,'等了很久才开始，时间安排不合理');

INSERT INTO feedback (activity_id, user_id, rating, content) VALUES
(15,'524030910001',5,'学姐讲得特别好，高数终于弄懂了！'),
(15,'524030910002',5,'非常及时的活动，考前救命稻草！'),
(15,'T001',5,'组织有序，答疑到位，希望每个学期都有'),
(15,'S001',4,'如果能增加物理的答疑时间就更好了'),
(15,'S002',5,'学霸助教们辛苦了，非常感谢'),
(15,'S003',5,'图书馆环境好，学习氛围浓厚'),
(15,'S004',4,'人有点多座位不够，建议申请更大的场地'),
(15,'S005',5,'期末考试稳了，满分好评！'),
(15,'S006',4,'答疑效率高，个别题目讲解可以更详细');
