-- Smart Ticket Platform 演示数据脚本
-- 执行前请先执行 schema.sql

USE smart_ticket_platform;

-- =========================
-- 角色
-- =========================

INSERT INTO sys_role (role_code, role_name)
VALUES
    ('USER', '普通用户'),
    ('STAFF', '处理人员'),
    ('ADMIN', '管理员')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name);

-- =========================
-- 演示用户
-- 密码统一为 123456
-- 这里使用 {noop} 方便本地演示，生产环境必须替换为安全哈希
-- =========================

INSERT INTO sys_user (username, password_hash, real_name, email, status)
VALUES
    ('user1', '{noop}123456', '普通员工', 'user1@example.com', 1),
    ('staff1', '{noop}123456', '处理人员', 'staff1@example.com', 1),
    ('admin1', '{noop}123456', '管理员', 'admin1@example.com', 1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    real_name = VALUES(real_name),
    email = VALUES(email),
    status = VALUES(status);

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'USER'
WHERE u.username = 'user1';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('USER', 'STAFF')
WHERE u.username = 'staff1';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('USER', 'STAFF', 'ADMIN')
WHERE u.username = 'admin1';

-- =========================
-- 工单组与队列
-- =========================

INSERT INTO ticket_group (group_name, group_code, owner_user_id, enabled)
SELECT '平台支持组', 'PLATFORM_SUPPORT', u.id, 1
FROM sys_user u
WHERE u.username = 'admin1'
ON DUPLICATE KEY UPDATE
    group_name = VALUES(group_name),
    owner_user_id = VALUES(owner_user_id),
    enabled = VALUES(enabled);

INSERT INTO ticket_queue (queue_name, queue_code, group_id, enabled)
SELECT '默认处理队列', 'DEFAULT_QUEUE', g.id, 1
FROM ticket_group g
WHERE g.group_code = 'PLATFORM_SUPPORT'
ON DUPLICATE KEY UPDATE
    queue_name = VALUES(queue_name),
    group_id = VALUES(group_id),
    enabled = VALUES(enabled);

INSERT IGNORE INTO ticket_queue_member (queue_id, user_id, enabled, last_assigned_at)
SELECT q.id, u.id, 1, NULL
FROM ticket_queue q
JOIN sys_user u ON u.username = 'staff1'
WHERE q.queue_code = 'DEFAULT_QUEUE';

INSERT IGNORE INTO ticket_queue_member (queue_id, user_id, enabled, last_assigned_at)
SELECT q.id, u.id, 1, NULL
FROM ticket_queue q
JOIN sys_user u ON u.username = 'admin1'
WHERE q.queue_code = 'DEFAULT_QUEUE';

-- =========================
-- SLA 策略
-- =========================

DELETE FROM ticket_sla_policy
WHERE policy_name IN (
    '系统中优先级默认 SLA',
    '账号高优先级 SLA',
    '环境高优先级 SLA'
);

INSERT INTO ticket_sla_policy (
    policy_name, category, priority, first_response_minutes, resolve_minutes, enabled
)
VALUES
    ('系统中优先级默认 SLA', 'SYSTEM', 'MEDIUM', 30, 240, 1),
    ('账号高优先级 SLA', 'ACCOUNT', 'HIGH', 15, 120, 1),
    ('环境高优先级 SLA', 'ENVIRONMENT', 'HIGH', 20, 180, 1);

-- =========================
-- 自动分派规则
-- =========================

INSERT INTO ticket_assignment_rule (
    rule_name, category, priority, target_group_id, target_queue_id, target_user_id, weight, enabled
)
SELECT
    '系统问题默认分派',
    'SYSTEM',
    'MEDIUM',
    g.id,
    q.id,
    NULL,
    100,
    1
FROM ticket_group g
JOIN ticket_queue q ON q.group_id = g.id
WHERE g.group_code = 'PLATFORM_SUPPORT'
  AND q.queue_code = 'DEFAULT_QUEUE'
  AND NOT EXISTS (
      SELECT 1
      FROM ticket_assignment_rule r
      WHERE r.rule_name = '系统问题默认分派'
  );

INSERT INTO ticket_assignment_rule (
    rule_name, category, priority, target_group_id, target_queue_id, target_user_id, weight, enabled
)
SELECT
    '账号问题高优先级直派 admin1',
    'ACCOUNT',
    'HIGH',
    g.id,
    q.id,
    u.id,
    200,
    1
FROM ticket_group g
JOIN ticket_queue q ON q.group_id = g.id
JOIN sys_user u ON u.username = 'admin1'
WHERE g.group_code = 'PLATFORM_SUPPORT'
  AND q.queue_code = 'DEFAULT_QUEUE'
  AND NOT EXISTS (
      SELECT 1
      FROM ticket_assignment_rule r
      WHERE r.rule_name = '账号问题高优先级直派 admin1'
  );

-- =========================
-- 审批模板
-- ACCESS_REQUEST 走两级审批：admin1 -> staff1
-- =========================

DELETE s
FROM ticket_approval_template_step s
JOIN ticket_approval_template t ON t.id = s.template_id
WHERE t.template_name = '权限申请双级审批';

DELETE FROM ticket_approval_template
WHERE template_name = '权限申请双级审批';

INSERT INTO ticket_approval_template (template_name, ticket_type, description, enabled)
VALUES ('权限申请双级审批', 'ACCESS_REQUEST', '用于演示权限申请工单的两级审批流程', 1);

INSERT INTO ticket_approval_template_step (template_id, step_order, step_name, approver_id)
SELECT t.id, 1, '管理员审批', u.id
FROM ticket_approval_template t
JOIN sys_user u ON u.username = 'admin1'
WHERE t.template_name = '权限申请双级审批';

INSERT INTO ticket_approval_template_step (template_id, step_order, step_name, approver_id)
SELECT t.id, 2, '处理人员复核', u.id
FROM ticket_approval_template t
JOIN sys_user u ON u.username = 'staff1'
WHERE t.template_name = '权限申请双级审批';

-- =========================
-- 演示知识库
-- =========================

INSERT INTO ticket_knowledge (ticket_id, content, content_summary, status)
SELECT
    0,
    '测试环境登录报错 500 时，可优先检查账号状态、权限配置、网关日志与数据库连接。',
    '测试环境登录失败排查建议',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM ticket_knowledge
    WHERE ticket_id = 0
);
