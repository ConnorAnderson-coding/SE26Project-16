-- ============================================================
-- 修复 user 表中双重编码（double encoding）造成的乱码
-- ============================================================
--
-- 现象：name / college 等字段显示为 mojibake（如 èµ„、çŽ‹å®‰）
-- 原因：UTF-8 字节被以 Latin-1 字符存入 utf8mb4 列
-- 原理：把存储的"假 Latin-1 字符"还原为字节，再按 UTF-8 重新解码
--
-- 用法：
--   1. 先执行"预览"段，确认要修复的行数和当前内容
--   2. 再执行"修复"段（已用 BEGIN/COMMIT 包裹，不满意可 ROLLBACK）
--   3. 最后执行"验证"段
--
-- 命令：
--   docker exec -i campus-mysql mysql -ucampus -pcampus123 campus_activity \
--     < database/fix-mojibake.sql
--
-- ⚠️ 务必先备份再运行：
--   docker exec campus-mysql mysqldump -ucampus -pcampus123 \
--     --default-character-set=utf8mb4 campus_activity user > backup_user_$(date +%Y%m%d).sql
-- ============================================================

SET NAMES utf8mb4;

-- ──────────── 预览：找出疑似双重编码的行 ────────────
SELECT '=== 预览：检测到的高位字节行 ===' AS info;

SELECT id, name, college,
       HEX(name)    AS name_hex,
       HEX(college) AS college_hex
FROM `user`
WHERE name    REGEXP '[\\x80-\\xFF]'
   OR college REGEXP '[\\x80-\\xFF]';

SELECT '=== 行数统计 ===' AS info;
SELECT COUNT(*) AS rows_to_fix
FROM `user`
WHERE name    REGEXP '[\\x80-\\xFF]'
   OR college REGEXP '[\\x80-\\xFF]';

-- ──────────── 修复（事务包裹，可回滚） ────────────
START TRANSACTION;

UPDATE `user`
SET
  name    = CONVERT(BINARY CONVERT(name    USING latin1) USING utf8mb4),
  college = CONVERT(BINARY CONVERT(college USING latin1) USING utf8mb4)
WHERE name    REGEXP '[\\x80-\\xFF]'
   OR college REGEXP '[\\x80-\\xFF]';

-- 如果结果不满意，在 MySQL CLI 里执行：ROLLBACK;
-- 满意则执行：COMMIT;
-- (CLI 模式下脚本结束默认不自动提交，需要显式 COMMIT)

SELECT '=== 修复后预览（请检查上方预览行是否已正常） ===' AS info;
SELECT id, name, college
FROM `user`
WHERE id IN ('S001', 'S002', 'S003', 'S004');

COMMIT;

-- ──────────── 验证 ────────────
SELECT '=== 验证：是否还有高位字节行 ===' AS info;
SELECT COUNT(*) AS remaining_corrupted_rows
FROM `user`
WHERE name    REGEXP '[\\x80-\\xFF]'
   OR college REGEXP '[\\x80-\\xFF]';

SELECT '=== 验证：S001~S004 最终显示 ===' AS info;
SELECT id, name, college FROM `user` WHERE id IN ('S001','S002','S003','S004');