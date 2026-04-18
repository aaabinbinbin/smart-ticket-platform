-- 企业智能工单协同平台初始数据脚本。
-- 执行前请先执行 schema.sql 创建数据库和表。

USE smart_ticket_platform;

-- 初始化系统角色。
-- USER：普通提单用户。
-- STAFF：工单处理人员。
-- ADMIN：管理员或组长，负责分配、监管和调整流程。
INSERT INTO sys_role (role_code, role_name)
VALUES
    ('USER', '普通用户'),
    ('STAFF', '处理人员'),
    ('ADMIN', '管理员')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- 初始化演示用户。
-- password_hash 当前使用 Spring Security 支持的 {noop}123456，便于本地演示直接登录。
-- 生产环境必须替换为 BCrypt 等安全哈希，不允许保存明文或 noop 密码。
INSERT INTO sys_user (username, password_hash, real_name, email, status)
VALUES
    ('user1', '{noop}123456', '普通员工', 'user1@example.com', 1),
    ('staff1', '{noop}123456', '处理人员', 'staff1@example.com', 1),
    ('admin1', '{noop}123456', '管理员', 'admin1@example.com', 1)
ON DUPLICATE KEY UPDATE
    real_name = VALUES(real_name),
    email = VALUES(email),
    status = VALUES(status);

-- 给 user1 绑定 USER 角色，表示普通员工，只具备基础提单能力。
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code = 'USER' WHERE u.username = 'user1';

-- 给 staff1 绑定 USER 和 STAFF 角色，表示既能提单也能处理工单。
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code IN ('USER', 'STAFF') WHERE u.username = 'staff1';

-- 给 admin1 绑定 USER、STAFF、ADMIN 角色，表示管理员具备完整演示权限。
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code IN ('USER', 'STAFF', 'ADMIN') WHERE u.username = 'admin1';
