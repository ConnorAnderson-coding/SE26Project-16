mysqldump: [Warning] Using a password on the command line interface can be insecure.
mysqldump: Error: 'Access denied; you need (at least one of) the PROCESS privilege(s) for this operation' when trying to dump tablespaces
-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: campus_activity
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学号/工号',
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'student' COMMENT 'student/teacher/admin',
  `college` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `grade` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `interests` json DEFAULT NULL COMMENT '兴趣标签数组',
  `available_time` json DEFAULT NULL COMMENT '可参与时间数组',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_user_role` (`role`),
  KEY `idx_user_college` (`college`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES ('524030910001','$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci','张三','student','软件学院','2024级','[\"AI\", \"摄影\", \"羽毛球\"]','[\"weekday_evening\", \"weekend\"]','2026-07-11 03:31:51.175','2026-07-11 03:31:51.175'),('524030910002','$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci','李四','student','计算机学院','2023级','[\"编程\", \"电竞\", \"篮球\"]','[\"weekend\"]','2026-07-11 03:31:51.175','2026-07-11 03:31:51.175'),('admin001','$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci','系统管理员','admin','软件学院','管理员','[]','[]','2026-07-11 03:31:51.175','2026-07-11 03:31:51.175'),('D001','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生01','student','电子信息与电气工程学院','2024','[\"AI\", \"讲座\"]','[\"weekday_evening\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D002','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生02','student','电子信息与电气工程学院','2024','[\"AI\", \"实践\"]','[\"weekend\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D003','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生03','student','电子信息与电气工程学院','2023','[\"志愿\", \"公益\"]','[\"weekday_noon\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D004','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生04','student','电子信息与电气工程学院','2023','[\"竞赛\"]','[\"weekday_evening\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D005','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生05','student','软件学院','2024','[\"就业\", \"分享\"]','[\"weekend\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D006','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生06','student','软件学院','2024','[\"AI\"]','[\"weekday_evening\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D007','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生07','student','软件学院','2023','[\"讲座\"]','[\"weekend\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D008','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生08','student','软件学院','2023','[\"实践\"]','[\"weekday_noon\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D009','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生09','student','管理学院','2024','[\"社团\"]','[\"weekday_evening\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D010','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生10','student','管理学院','2024','[\"创业\"]','[\"weekend\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D011','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生11','student','管理学院','2023','[\"志愿\"]','[\"weekday_noon\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D012','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生12','student','管理学院','2023','[\"竞赛\"]','[\"weekday_evening\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D013','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生13','student','设计学院','2024','[\"设计\"]','[\"weekend\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D014','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生14','student','设计学院','2024','[\"艺术\"]','[\"weekday_evening\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D015','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生15','student','设计学院','2023','[\"分享\"]','[\"weekday_noon\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D016','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生16','student','设计学院','2023','[\"实践\"]','[\"weekend\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D017','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生17','student','电子信息与电气工程学院','2022','[\"AI\"]','[\"weekday_evening\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D018','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生18','student','软件学院','2022','[\"就业\"]','[\"weekend\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D019','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生19','student','管理学院','2022','[\"创业\"]','[\"weekday_noon\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('D020','$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo','演示学生20','student','设计学院','2022','[\"设计\"]','[\"weekday_evening\"]','2026-07-13 12:15:26.434','2026-07-13 12:15:26.434'),('S001','','æŽå››','student','ç”µå­ä¿¡æ¯ä¸Žç”µæ°”å·¥ç¨‹å­¦é™¢','2024çº§',NULL,NULL,'2026-07-13 10:35:24.890','2026-07-13 10:35:24.890'),('S002','','çŽ‹äº”','student','æ•°å­¦ç§‘å­¦å­¦é™¢','2023çº§',NULL,NULL,'2026-07-13 10:35:24.890','2026-07-13 10:35:24.890'),('S003','','èµµå…­','student','åª’ä½“ä¸Žä¼ æ’­å­¦é™¢','2024çº§',NULL,NULL,'2026-07-13 10:35:24.890','2026-07-13 10:35:24.890'),('S004','','å­™ä¸ƒ','student','è®¡ç®—æœºç§‘å­¦ä¸Žå·¥ç¨‹å­¦é™¢','2025çº§',NULL,NULL,'2026-07-13 10:35:24.890','2026-07-13 10:35:24.890'),('T001','$2b$10$cnj17VVfRsVMXC.xjroOHeyM6.GB4DcpaaL5r6y/6hqIctsnsv6Ci','王老师','teacher','软件学院','教师','[\"AI\", \"创业\"]','[\"weekday_morning\", \"weekday_afternoon\"]','2026-07-11 03:31:51.175','2026-07-11 03:31:51.175');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-14  5:27:16
