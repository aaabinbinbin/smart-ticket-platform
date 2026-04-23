-- Smart Ticket Platform 数据库初始化脚本
-- 适用场景：全新初始化本地 MySQL 数据库
-- 建议做法：
-- 1. 如果你本地已经有旧版本表结构，先删除旧库或手动清空旧表
-- 2. 再执行本脚本，确保表结构与当前代码一致

CREATE DATABASE IF NOT EXISTS smart_ticket_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_ticket_platform;
SET NAMES utf8mb4;

-- =========================
-- 系统用户与权限
-- =========================

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    real_name VARCHAR(64) NOT NULL COMMENT '真实姓名',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色主键',
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码，如 USER/STAFF/ADMIN',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户角色关联主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_sys_user_role_user_id (user_id),
    INDEX idx_sys_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- 工单主表与基础信息
-- =========================

CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单主键',
    ticket_no VARCHAR(32) NOT NULL UNIQUE COMMENT '业务工单号',
    title VARCHAR(200) NOT NULL COMMENT '工单标题',
    description TEXT NOT NULL COMMENT '工单描述',
    ticket_type VARCHAR(64) NOT NULL COMMENT '工单类型',
    category VARCHAR(64) NOT NULL COMMENT '工单分类',
    priority VARCHAR(32) NOT NULL COMMENT '工单优先级',
    status VARCHAR(32) NOT NULL COMMENT '工单状态',
    creator_id BIGINT NOT NULL COMMENT '提单人用户 ID',
    assignee_id BIGINT DEFAULT NULL COMMENT '当前处理人用户 ID',
    group_id BIGINT DEFAULT NULL COMMENT '所属工单组 ID',
    queue_id BIGINT DEFAULT NULL COMMENT '所属队列 ID',
    solution_summary TEXT DEFAULT NULL COMMENT '解决方案摘要',
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '创建来源',
    idempotency_key VARCHAR(128) DEFAULT NULL COMMENT '幂等键',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ticket_creator_id (creator_id),
    INDEX idx_ticket_assignee_id (assignee_id),
    INDEX idx_ticket_group_id (group_id),
    INDEX idx_ticket_queue_id (queue_id),
    INDEX idx_ticket_status (status),
    INDEX idx_ticket_type (ticket_type),
    INDEX idx_ticket_category (category),
    INDEX idx_ticket_priority (priority),
    INDEX idx_ticket_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_type_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单类型扩展信息主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    profile_schema VARCHAR(64) NOT NULL COMMENT '扩展信息模型编码',
    profile_data JSON NOT NULL COMMENT '扩展信息 JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_type_profile_ticket_id (ticket_id),
    INDEX idx_ticket_type_profile_schema (profile_schema)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单评论主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    commenter_id BIGINT NOT NULL COMMENT '评论人用户 ID',
    comment_type VARCHAR(32) NOT NULL COMMENT '评论类型',
    content TEXT NOT NULL COMMENT '评论内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_ticket_comment_ticket_id (ticket_id),
    INDEX idx_ticket_comment_commenter_id (commenter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单操作日志主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    operator_id BIGINT NOT NULL COMMENT '操作人用户 ID',
    operation_type VARCHAR(64) NOT NULL COMMENT '操作类型',
    operation_desc VARCHAR(500) NOT NULL COMMENT '操作描述',
    before_value TEXT DEFAULT NULL COMMENT '变更前内容',
    after_value TEXT DEFAULT NULL COMMENT '变更后内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_ticket_operation_log_ticket_id (ticket_id),
    INDEX idx_ticket_operation_log_operator_id (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单附件主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_url VARCHAR(500) NOT NULL COMMENT '文件访问地址',
    file_type VARCHAR(64) DEFAULT NULL COMMENT '文件类型',
    file_size BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
    uploader_id BIGINT NOT NULL COMMENT '上传人用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_ticket_attachment_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- RAG 知识库
-- =========================

CREATE TABLE IF NOT EXISTS ticket_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单知识主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    content TEXT NOT NULL COMMENT '知识正文',
    content_summary VARCHAR(1000) DEFAULT NULL COMMENT '知识摘要',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_knowledge_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_knowledge_candidate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识候选主键',
    ticket_id BIGINT NOT NULL COMMENT '来源工单 ID',
    status VARCHAR(32) NOT NULL COMMENT '候选状态：AUTO_APPROVED/AUTO_REJECTED/MANUAL_REVIEW',
    quality_score INT NOT NULL DEFAULT 0 COMMENT '质量分',
    decision VARCHAR(32) NOT NULL COMMENT '准入决策',
    reason VARCHAR(1000) DEFAULT NULL COMMENT '决策原因',
    sensitive_risk VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '敏感信息风险',
    reviewed_at DATETIME DEFAULT NULL COMMENT '人工复核时间',
    reviewed_by BIGINT DEFAULT NULL COMMENT '人工复核人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_knowledge_candidate_ticket_id (ticket_id),
    INDEX idx_ticket_knowledge_candidate_status (status, updated_at),
    INDEX idx_ticket_knowledge_candidate_score (quality_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_knowledge_embedding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识切片向量主键',
    knowledge_id BIGINT NOT NULL COMMENT '知识 ID',
    chunk_index INT NOT NULL COMMENT '切片序号',
    chunk_text TEXT NOT NULL COMMENT '切片文本',
    embedding_vector TEXT DEFAULT NULL COMMENT '向量内容，JSON 字符串形式',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_ticket_knowledge_embedding_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- 工单组、队列与自动分派
-- =========================

CREATE TABLE IF NOT EXISTS ticket_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单组主键',
    group_name VARCHAR(128) NOT NULL COMMENT '工单组名称',
    group_code VARCHAR(64) NOT NULL COMMENT '工单组编码',
    owner_user_id BIGINT DEFAULT NULL COMMENT '负责人用户 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_group_code (group_code),
    INDEX idx_ticket_group_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_queue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '队列主键',
    queue_name VARCHAR(128) NOT NULL COMMENT '队列名称',
    queue_code VARCHAR(64) NOT NULL COMMENT '队列编码',
    group_id BIGINT NOT NULL COMMENT '所属工单组 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_queue_code (queue_code),
    INDEX idx_ticket_queue_group_id (group_id),
    INDEX idx_ticket_queue_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_queue_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '队列成员主键',
    queue_id BIGINT NOT NULL COMMENT '队列 ID',
    user_id BIGINT NOT NULL COMMENT '成员用户 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
    last_assigned_at DATETIME DEFAULT NULL COMMENT '最近一次被分配时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_queue_member (queue_id, user_id),
    INDEX idx_ticket_queue_member_queue_id (queue_id),
    INDEX idx_ticket_queue_member_user_id (user_id),
    INDEX idx_ticket_queue_member_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_assignment_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自动分派规则主键',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    category VARCHAR(64) DEFAULT NULL COMMENT '命中的工单分类',
    priority VARCHAR(64) DEFAULT NULL COMMENT '命中的工单优先级',
    target_group_id BIGINT DEFAULT NULL COMMENT '目标工单组 ID',
    target_queue_id BIGINT DEFAULT NULL COMMENT '目标队列 ID',
    target_user_id BIGINT DEFAULT NULL COMMENT '目标处理人用户 ID',
    weight INT NOT NULL DEFAULT 0 COMMENT '规则权重',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ticket_assignment_rule_match (category, priority, enabled, weight),
    INDEX idx_ticket_assignment_rule_target_group (target_group_id),
    INDEX idx_ticket_assignment_rule_target_queue (target_queue_id),
    INDEX idx_ticket_assignment_rule_target_user (target_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- SLA
-- =========================

CREATE TABLE IF NOT EXISTS ticket_sla_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'SLA 策略主键',
    policy_name VARCHAR(128) NOT NULL COMMENT '策略名称',
    category VARCHAR(64) DEFAULT NULL COMMENT '适用工单分类',
    priority VARCHAR(64) DEFAULT NULL COMMENT '适用工单优先级',
    first_response_minutes INT NOT NULL COMMENT '首次响应时限（分钟）',
    resolve_minutes INT NOT NULL COMMENT '解决时限（分钟）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ticket_sla_policy_match (category, priority, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_sla_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'SLA 实例主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    policy_id BIGINT NOT NULL COMMENT 'SLA 策略 ID',
    first_response_deadline DATETIME NOT NULL COMMENT '首次响应截止时间',
    resolve_deadline DATETIME NOT NULL COMMENT '解决截止时间',
    breached TINYINT NOT NULL DEFAULT 0 COMMENT '是否已违约：1-是，0-否',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_sla_ticket_id (ticket_id),
    INDEX idx_ticket_sla_policy_id (policy_id),
    INDEX idx_ticket_sla_deadline (resolve_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '站内通知主键',
    ticket_id BIGINT DEFAULT NULL COMMENT '关联工单 ID',
    receiver_user_id BIGINT NOT NULL COMMENT '接收用户 ID',
    channel VARCHAR(32) NOT NULL DEFAULT 'IN_APP' COMMENT '通知渠道',
    notification_type VARCHAR(64) NOT NULL COMMENT '通知类型',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    content VARCHAR(1000) NOT NULL COMMENT '通知内容',
    read_status TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：1-已读，0-未读',
    read_at DATETIME DEFAULT NULL COMMENT '已读时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ticket_notification_receiver (receiver_user_id, read_status, created_at),
    INDEX idx_ticket_notification_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- Agent 执行 Trace
-- =========================

CREATE TABLE IF NOT EXISTS agent_trace_record (
    trace_id VARCHAR(64) PRIMARY KEY COMMENT 'Trace ID',
    session_id VARCHAR(128) NOT NULL COMMENT '会话 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    raw_input TEXT DEFAULT NULL COMMENT '用户原始输入',
    intent VARCHAR(64) DEFAULT NULL COMMENT '识别到的意图',
    confidence DOUBLE DEFAULT NULL COMMENT '意图置信度',
    plan_stage VARCHAR(64) DEFAULT NULL COMMENT '当前计划阶段',
    triggered_skill VARCHAR(128) DEFAULT NULL COMMENT '触发的 Skill',
    parameter_summary TEXT DEFAULT NULL COMMENT '参数抽取摘要',
    prompt_version VARCHAR(128) DEFAULT NULL COMMENT '使用的 Prompt 版本',
    spring_ai_used TINYINT NOT NULL DEFAULT 0 COMMENT '是否使用 Spring AI',
    fallback_used TINYINT NOT NULL DEFAULT 0 COMMENT '是否使用确定性 fallback',
    final_reply TEXT DEFAULT NULL COMMENT '最终回复',
    elapsed_millis BIGINT NOT NULL DEFAULT 0 COMMENT '执行耗时，毫秒',
    status VARCHAR(64) DEFAULT NULL COMMENT '执行状态',
    failure_type VARCHAR(64) DEFAULT NULL COMMENT '失败类型',
    step_json JSON DEFAULT NULL COMMENT '步骤级 Trace',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_agent_trace_session_id (session_id, created_at),
    INDEX idx_agent_trace_user_id (user_id, created_at),
    INDEX idx_agent_trace_failure_type (failure_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================
-- 审批流
-- =========================

CREATE TABLE IF NOT EXISTS ticket_approval_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审批模板主键',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    ticket_type VARCHAR(64) NOT NULL COMMENT '适用工单类型',
    description VARCHAR(500) DEFAULT NULL COMMENT '模板说明',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ticket_approval_template_type (ticket_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_approval_template_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审批模板步骤主键',
    template_id BIGINT NOT NULL COMMENT '模板 ID',
    step_order INT NOT NULL COMMENT '步骤顺序，从 1 开始',
    step_name VARCHAR(128) NOT NULL COMMENT '步骤名称',
    approver_id BIGINT NOT NULL COMMENT '审批人用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_approval_template_step (template_id, step_order),
    INDEX idx_ticket_approval_template_step_template_id (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单审批主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    template_id BIGINT DEFAULT NULL COMMENT '审批模板 ID',
    current_step_order INT DEFAULT NULL COMMENT '当前审批步骤顺序',
    approval_status VARCHAR(32) NOT NULL COMMENT '审批状态',
    approver_id BIGINT NOT NULL COMMENT '当前审批人用户 ID',
    requested_by BIGINT NOT NULL COMMENT '提交审批人用户 ID',
    submit_comment VARCHAR(500) DEFAULT NULL COMMENT '提交审批说明',
    decision_comment VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    submitted_at DATETIME NOT NULL COMMENT '提交时间',
    decided_at DATETIME DEFAULT NULL COMMENT '审批完成时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_approval_ticket_id (ticket_id),
    INDEX idx_ticket_approval_status (approval_status),
    INDEX idx_ticket_approval_approver_id (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_approval_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审批实例步骤主键',
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    approval_id BIGINT NOT NULL COMMENT '审批记录 ID',
    step_order INT NOT NULL COMMENT '步骤顺序，从 1 开始',
    step_name VARCHAR(128) NOT NULL COMMENT '步骤名称',
    approver_id BIGINT NOT NULL COMMENT '审批人用户 ID',
    step_status VARCHAR(32) NOT NULL COMMENT '步骤状态',
    decision_comment VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    decided_at DATETIME DEFAULT NULL COMMENT '审批完成时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_approval_step (approval_id, step_order),
    INDEX idx_ticket_approval_step_ticket_id (ticket_id),
    INDEX idx_ticket_approval_step_status (step_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
