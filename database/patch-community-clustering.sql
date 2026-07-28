USE campus_activity;

CREATE TABLE IF NOT EXISTS clustering_run (
  run_id                 VARCHAR(64)  NOT NULL PRIMARY KEY,
  run_version            VARCHAR(64)  NOT NULL,
  algorithm_name         VARCHAR(32)  NOT NULL,
  cluster_count          INT          NOT NULL,
  random_state           INT          NOT NULL,
  run_status             VARCHAR(16)  NOT NULL,
  active_slot            VARCHAR(16)  NULL,
  sample_count           INT          NULL,
  feature_dimension      INT          NULL,
  feature_schema_version VARCHAR(64)  NOT NULL,
  parameters_json        JSON         NOT NULL,
  feature_manifest_json  JSON         NOT NULL,
  metrics_json           JSON         NULL,
  started_at             DATETIME(3)  NULL,
  finished_at            DATETIME(3)  NULL,
  error_code             VARCHAR(64)  NULL,
  error_message          VARCHAR(1000) NULL,
  created_by             VARCHAR(32)  NOT NULL,
  created_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_clustering_run_creator FOREIGN KEY (created_by) REFERENCES `user`(id),
  CONSTRAINT ck_clustering_run_cluster_count CHECK (cluster_count >= 2),
  CONSTRAINT ck_clustering_run_algorithm CHECK (algorithm_name = 'KMEANS'),
  CONSTRAINT ck_clustering_run_random_state CHECK (random_state = 42),
  CONSTRAINT ck_clustering_run_sample_count CHECK (sample_count IS NULL OR sample_count >= 0),
  CONSTRAINT ck_clustering_run_feature_dimension CHECK (feature_dimension IS NULL OR feature_dimension > 0),
  CONSTRAINT ck_clustering_run_active_slot_state CHECK (
    (run_status IN ('PENDING', 'RUNNING') AND active_slot = 'GLOBAL')
    OR (run_status IN ('SUCCESS', 'FAILED') AND active_slot IS NULL)
  ),
  UNIQUE KEY uk_clustering_run_version (run_version),
  UNIQUE KEY uk_clustering_run_active_slot (active_slot),
  INDEX idx_clustering_run_status_finished (run_status, finished_at),
  INDEX idx_clustering_run_created_id (created_at, run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS clustering_run_input (
  input_id             VARCHAR(64) NOT NULL PRIMARY KEY,
  run_id               VARCHAR(64) NOT NULL,
  user_id              VARCHAR(32) NOT NULL,
  sample_order         INT         NOT NULL,
  feature_payload_json JSON        NOT NULL,
  CONSTRAINT fk_clustering_run_input_run FOREIGN KEY (run_id)
    REFERENCES clustering_run(run_id) ON DELETE CASCADE,
  CONSTRAINT fk_clustering_run_input_user FOREIGN KEY (user_id)
    REFERENCES `user`(id),
  CONSTRAINT ck_clustering_run_input_order CHECK (sample_order >= 0),
  UNIQUE KEY uk_clustering_run_input_user (run_id, user_id),
  UNIQUE KEY uk_clustering_run_input_order (run_id, sample_order),
  INDEX idx_clustering_run_input_order (run_id, sample_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_cluster (
  community_id         VARCHAR(64)  NOT NULL PRIMARY KEY,
  run_id               VARCHAR(64)  NOT NULL,
  cluster_no           INT          NOT NULL,
  community_name       VARCHAR(100) NOT NULL,
  community_description VARCHAR(500) NULL,
  member_count         INT          NOT NULL,
  top_interests_json   JSON         NOT NULL,
  display_color        VARCHAR(16)  NOT NULL,
  CONSTRAINT fk_community_cluster_run FOREIGN KEY (run_id)
    REFERENCES clustering_run(run_id) ON DELETE CASCADE,
  CONSTRAINT ck_community_cluster_no CHECK (cluster_no >= 0),
  CONSTRAINT ck_community_cluster_members CHECK (member_count > 0),
  UNIQUE KEY uk_community_cluster_run_no (run_id, cluster_no),
  UNIQUE KEY uk_community_cluster_id_run (community_id, run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_membership (
  membership_id      VARCHAR(64) NOT NULL PRIMARY KEY,
  run_id             VARCHAR(64) NOT NULL,
  community_id       VARCHAR(64) NOT NULL,
  user_id            VARCHAR(32) NOT NULL,
  coordinate_x       DOUBLE      NOT NULL,
  coordinate_y       DOUBLE      NOT NULL,
  distance_to_center DOUBLE      NOT NULL,
  assigned_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_community_membership_run FOREIGN KEY (run_id)
    REFERENCES clustering_run(run_id) ON DELETE CASCADE,
  CONSTRAINT fk_community_membership_community_run FOREIGN KEY (community_id, run_id)
    REFERENCES community_cluster(community_id, run_id) ON DELETE CASCADE,
  CONSTRAINT fk_community_membership_user FOREIGN KEY (user_id)
    REFERENCES `user`(id),
  CONSTRAINT ck_community_membership_x CHECK (coordinate_x >= 0 AND coordinate_x <= 100),
  CONSTRAINT ck_community_membership_y CHECK (coordinate_y >= 0 AND coordinate_y <= 100),
  CONSTRAINT ck_community_membership_distance CHECK (distance_to_center >= 0),
  UNIQUE KEY uk_community_membership_run_user (run_id, user_id),
  INDEX idx_community_membership_community (community_id, run_id),
  INDEX idx_community_membership_user (user_id),
  INDEX idx_community_membership_admin_page (community_id, distance_to_center, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
