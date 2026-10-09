-- Moves invariants that so far were only enforced in Java into the schema, and indexes the
-- queries the dashboard, the rollback logic and the history pages run most often.
--
-- Additive and non-destructive: no column or row is changed or removed. Every row the
-- application has written already satisfies these constraints; should a manually edited row
-- not, the migration fails and is rolled back (PostgreSQL DDL is transactional) instead of
-- altering data. Portable SQL, so H2 in PostgreSQL mode runs the same file in tests.

-- A deployment's application must be the application that owns its environment. Before, the
-- two foreign keys were independent and could point at unrelated rows.
ALTER TABLE environments
    ADD CONSTRAINT uq_environments_id_application UNIQUE (id, application_id);
ALTER TABLE deployments
    ADD CONSTRAINT fk_deployments_environment_application FOREIGN KEY (environment_id, application_id)
        REFERENCES environments (id, application_id) ON DELETE CASCADE;

-- Lifecycle timestamps follow the status (see Deployment.transitionTo): PENDING has not
-- started, RUNNING has started but not completed, finished deployments have both.
ALTER TABLE deployments
    ADD CONSTRAINT ck_deployments_lifecycle_timestamps CHECK (
        (status = 'PENDING' AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK') AND started_at IS NOT NULL AND completed_at IS NOT NULL));
ALTER TABLE deployments
    ADD CONSTRAINT ck_deployments_completed_after_started CHECK (completed_at IS NULL OR completed_at >= started_at);

-- Deployments reference an immutable, non-empty image tag and carry a version label.
ALTER TABLE deployments
    ADD CONSTRAINT ck_deployments_image_tag_immutable CHECK (LENGTH(TRIM(image_tag)) > 0 AND LOWER(image_tag) <> 'latest');
ALTER TABLE deployments
    ADD CONSTRAINT ck_deployments_version_not_blank CHECK (LENGTH(TRIM(version)) > 0);

-- An ACTIVE environment is serving a version; probe results always have a timestamp, and a
-- failure detail only exists for a failed probe.
ALTER TABLE environments
    ADD CONSTRAINT ck_environments_active_has_version CHECK (status <> 'ACTIVE' OR current_version IS NOT NULL);
ALTER TABLE environments
    ADD CONSTRAINT ck_environments_probe_checked_at CHECK (health_status = 'UNKNOWN' OR last_health_check_at IS NOT NULL);
ALTER TABLE environments
    ADD CONSTRAINT ck_environments_health_detail CHECK (last_health_detail IS NULL OR health_status = 'UNHEALTHY');

-- Live deployment and rollback candidates: WHERE environment_id = ? AND status = 'SUCCESS'
-- ORDER BY completed_at DESC, and the in-progress check on (environment_id, status).
CREATE INDEX idx_deployments_environment_status_completed ON deployments (environment_id, status, completed_at);

-- Global history page, newest first, optionally filtered by status. The composite index
-- replaces the single-column status index.
CREATE INDEX idx_deployments_created ON deployments (created_at, id);
DROP INDEX idx_deployments_status;
CREATE INDEX idx_deployments_status_created ON deployments (status, created_at, id);

-- Recent deployments of an application, newest first; replaces the single-column index.
DROP INDEX idx_deployments_application;
CREATE INDEX idx_deployments_application_created ON deployments (application_id, created_at, id);
