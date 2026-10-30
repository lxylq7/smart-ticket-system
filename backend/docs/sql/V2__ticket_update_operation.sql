-- Run once on the MySQL 8 schema from the database-design stage.
-- DDL implicitly commits in MySQL. This changes only the allowed history action values.
USE smart_ticket;

ALTER TABLE ticket_operation_history
    DROP CHECK chk_ticket_history_operation_type,
    ADD CONSTRAINT chk_ticket_history_operation_type CHECK (
        operation_type IN (
            'CREATE', 'UPDATE', 'STATUS_CHANGE', 'ASSIGN', 'PRIORITY_CHANGE',
            'PUBLIC_REPLY', 'INTERNAL_NOTE', 'AI_CLASSIFY', 'AI_CLASSIFY_FAILED'
        )
    );
