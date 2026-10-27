DELETE FROM ticket_message;
DELETE FROM ticket_operation_history;
DELETE FROM ticket;
DELETE FROM sys_user_role;
DELETE FROM sys_role;
DELETE FROM sys_user;

INSERT INTO sys_role (id, code, name) VALUES
    (1, 'USER', '普通用户'), (2, 'AGENT', '客服'), (3, 'ADMIN', '管理员');

-- Each test replaces these placeholder hashes with BCrypt("TestPass123!").
INSERT INTO sys_user (id, username, password_hash, display_name, status) VALUES
    (1, 'alice', 'test-placeholder', 'Alice', 'ACTIVE'),
    (2, 'bob', 'test-placeholder', 'Bob', 'ACTIVE'),
    (3, 'agent', 'test-placeholder', 'Agent', 'ACTIVE'),
    (4, 'agent2', 'test-placeholder', 'Agent2', 'ACTIVE'),
    (5, 'admin', 'test-placeholder', 'Admin', 'ACTIVE'),
    (6, 'disabled', 'test-placeholder', 'Disabled', 'DISABLED');

INSERT INTO sys_user_role (user_id, role_id) VALUES
    (1, 1), (2, 1), (3, 2), (4, 2), (5, 3), (6, 1);

INSERT INTO ticket (id, ticket_no, creator_id, assignee_id, title, description, status) VALUES
    (101, 'TEST-101', 1, NULL, 'Alice new', 'Alice new description', 'PENDING'),
    (102, 'TEST-102', 2, NULL, 'Bob new', 'Bob new description', 'PENDING'),
    (103, 'TEST-103', 1, 3, 'Alice assigned', 'Alice assigned description', 'ASSIGNED'),
    (104, 'TEST-104', 2, 4, 'Bob assigned', 'Bob assigned description', 'ASSIGNED'),
    (105, 'TEST-105', 1, 3, 'Alice resolved', 'Alice resolved description', 'RESOLVED');
