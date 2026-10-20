-- MySQL 8.0.16+. Run after V2, with ALL application instances stopped.
-- DDL implicitly commits. Back up first. Do not run this as a single rollback-able transaction.
-- Step 1 expands CHECKs so both old and new values are valid during conversion.
USE smart_ticket;

ALTER TABLE ticket
    DROP CHECK chk_ticket_status,
    ADD CONSTRAINT chk_ticket_status CHECK (status IN (
        'NEW', 'IN_PROGRESS', 'PENDING', 'ASSIGNED', 'PROCESSING', 'WAITING_USER', 'RESOLVED', 'CLOSED'
    ));

ALTER TABLE ticket_operation_history
    DROP CHECK chk_ticket_history_from_status,
    DROP CHECK chk_ticket_history_to_status,
    ADD CONSTRAINT chk_ticket_history_from_status CHECK (from_status IS NULL OR from_status IN (
        'NEW', 'IN_PROGRESS', 'PENDING', 'ASSIGNED', 'PROCESSING', 'WAITING_USER', 'RESOLVED', 'CLOSED'
    )),
    ADD CONSTRAINT chk_ticket_history_to_status CHECK (to_status IS NULL OR to_status IN (
        'NEW', 'IN_PROGRESS', 'PENDING', 'ASSIGNED', 'PROCESSING', 'WAITING_USER', 'RESOLVED', 'CLOSED'
    ));

-- Step 2 changes the names without adding fictitious business operations to history.
-- Increment versions to invalidate old clients. Already migrated rows are left intact.
START TRANSACTION;
UPDATE ticket
SET status = CASE status WHEN 'NEW' THEN 'PENDING' WHEN 'IN_PROGRESS' THEN 'PROCESSING' ELSE status END,
    version = version + 1
WHERE status IN ('NEW', 'IN_PROGRESS');

UPDATE ticket_operation_history
SET from_status = CASE from_status WHEN 'NEW' THEN 'PENDING' WHEN 'IN_PROGRESS' THEN 'PROCESSING' ELSE from_status END,
    to_status = CASE to_status WHEN 'NEW' THEN 'PENDING' WHEN 'IN_PROGRESS' THEN 'PROCESSING' ELSE to_status END
WHERE from_status IN ('NEW', 'IN_PROGRESS') OR to_status IN ('NEW', 'IN_PROGRESS');
COMMIT;

-- Step 3 rejects old values and updates the default. Can be rerun after an interrupted migration.
ALTER TABLE ticket
    DROP CHECK chk_ticket_status,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING、ASSIGNED、PROCESSING、WAITING_USER、RESOLVED、CLOSED',
    ADD CONSTRAINT chk_ticket_status CHECK (status IN (
        'PENDING', 'ASSIGNED', 'PROCESSING', 'WAITING_USER', 'RESOLVED', 'CLOSED'
    ));

ALTER TABLE ticket_operation_history
    DROP CHECK chk_ticket_history_from_status,
    DROP CHECK chk_ticket_history_to_status,
    ADD CONSTRAINT chk_ticket_history_from_status CHECK (from_status IS NULL OR from_status IN (
        'PENDING', 'ASSIGNED', 'PROCESSING', 'WAITING_USER', 'RESOLVED', 'CLOSED'
    )),
    ADD CONSTRAINT chk_ticket_history_to_status CHECK (to_status IS NULL OR to_status IN (
        'PENDING', 'ASSIGNED', 'PROCESSING', 'WAITING_USER', 'RESOLVED', 'CLOSED'
    ));
