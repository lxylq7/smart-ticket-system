-- OPTIONAL: reset only the three demo accounts from the database-design stage.
-- Development use only. Never run this against production accounts.
-- Verified BCrypt password: DemoPass123!
USE smart_ticket;

UPDATE sys_user
SET password_hash = '$2a$10$y6Ui9lCUl.RZSkxOzyGDbOugEGdwUN3FN4MCLRvd1kdWhd8hrm84u'
WHERE username IN ('alice', 'agent01', 'admin');
