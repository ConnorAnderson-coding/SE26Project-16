-- 清理压测产生的 Probe*/JMeter* 脏活动
-- Hibernate 可能生成无 ON DELETE CASCADE 的外键，故先删子表。
-- 云端示例：
--   docker exec -i campus-mysql mysql -ucampus -pcampus123 campus_activity < cleanup-probe-jmeter-activities.sql

SELECT COUNT(*) AS probe_jmeter_count
FROM activity
WHERE title LIKE 'Probe%'
   OR title LIKE 'JMeter%'
   OR title LIKE 'EditDebug%';

DELETE ci FROM check_in ci
INNER JOIN activity a ON a.id = ci.activity_id
WHERE a.title LIKE 'Probe%' OR a.title LIKE 'JMeter%' OR a.title LIKE 'EditDebug%';

DELETE r FROM registration r
INNER JOIN activity a ON a.id = r.activity_id
WHERE a.title LIKE 'Probe%' OR a.title LIKE 'JMeter%' OR a.title LIKE 'EditDebug%';

DELETE f FROM favorite f
INNER JOIN activity a ON a.id = f.activity_id
WHERE a.title LIKE 'Probe%' OR a.title LIKE 'JMeter%' OR a.title LIKE 'EditDebug%';

DELETE v FROM activity_view v
INNER JOIN activity a ON a.id = v.activity_id
WHERE a.title LIKE 'Probe%' OR a.title LIKE 'JMeter%' OR a.title LIKE 'EditDebug%';

DELETE fb FROM feedback fb
INNER JOIN activity a ON a.id = fb.activity_id
WHERE a.title LIKE 'Probe%' OR a.title LIKE 'JMeter%' OR a.title LIKE 'EditDebug%';

DELETE ar FROM activity_record ar
INNER JOIN activity a ON a.id = ar.activity_id
WHERE a.title LIKE 'Probe%' OR a.title LIKE 'JMeter%' OR a.title LIKE 'EditDebug%';

DELETE aa FROM activity_analysis aa
INNER JOIN activity a ON a.id = aa.activity_id
WHERE a.title LIKE 'Probe%' OR a.title LIKE 'JMeter%' OR a.title LIKE 'EditDebug%';

DELETE FROM activity
WHERE title LIKE 'Probe%'
   OR title LIKE 'JMeter%'
   OR title LIKE 'EditDebug%';

SELECT ROW_COUNT() AS deleted_activities;

SELECT COUNT(*) AS remaining
FROM activity
WHERE title LIKE 'Probe%'
   OR title LIKE 'JMeter%'
   OR title LIKE 'EditDebug%';
