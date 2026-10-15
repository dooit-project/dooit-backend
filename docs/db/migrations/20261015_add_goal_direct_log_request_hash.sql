-- Preserve the original create payload fingerprint across direct log edits and cancellation.
-- Treat opaque idempotency keys as case-sensitive in MySQL.
-- Apply after 20261014_add_goal_journey.sql, after backup/schema verification.
-- NULL permits pre-existing rows; their original create payload cannot be reconstructed reliably.
ALTER TABLE GOAL_DIRECT_LOG
    ADD COLUMN CREATE_REQUEST_HASH VARCHAR(64) NULL,
    MODIFY COLUMN IDEMPOTENCY_KEY VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
