USE campus_activity;

-- Existing deployments may contain snapshots created from activity.favorite_count.
-- Reconcile them once with the favorite detail table; new snapshots are calculated
-- directly from favorite records by AnalyticsEngine.
UPDATE activity_analysis aa
SET aa.favorite_count_snapshot = (
  SELECT COUNT(*)
  FROM favorite f
  WHERE f.activity_id = aa.activity_id
)
WHERE aa.snapshot_at IS NOT NULL;
