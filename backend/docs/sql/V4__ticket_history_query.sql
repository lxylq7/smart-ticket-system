-- Execute once on an existing MySQL 8 database after stopping the application.
-- DDL implicitly commits in MySQL.
USE smart_ticket;

ALTER TABLE ticket_operation_history
    ADD INDEX idx_ticket_history_ticket_created (ticket_id, created_at, id);
