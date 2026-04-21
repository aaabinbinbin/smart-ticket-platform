-- P1/P2 draft schema only.
-- This file is not an executable migration for the current stage.
-- Purpose: keep future table boundaries visible without implementing complex business flows.

-- P1: ticket group and queue draft.
CREATE TABLE IF NOT EXISTS ticket_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_name VARCHAR(128) NOT NULL COMMENT '工单组名称',
    group_code VARCHAR(64) NOT NULL COMMENT '工单组编码',
    owner_user_id BIGINT NULL COMMENT '组负责人用户 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ticket_group_code (group_code)
) COMMENT='P1 草案：工单组';

CREATE TABLE IF NOT EXISTS ticket_queue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    queue_name VARCHAR(128) NOT NULL COMMENT '队列名称',
    queue_code VARCHAR(64) NOT NULL COMMENT '队列编码',
    group_id BIGINT NOT NULL COMMENT '所属工单组 ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ticket_queue_code (queue_code),
    KEY idx_ticket_queue_group_id (group_id)
) COMMENT='P1 草案：工单队列';

-- P1: SLA draft.
CREATE TABLE IF NOT EXISTS ticket_sla_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_name VARCHAR(128) NOT NULL COMMENT 'SLA 策略名称',
    category VARCHAR(64) NULL COMMENT '适用工单分类',
    priority VARCHAR(64) NULL COMMENT '适用优先级',
    first_response_minutes INT NOT NULL COMMENT '首次响应时限',
    resolve_minutes INT NOT NULL COMMENT '解决时限',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='P1 草案：SLA 策略';

CREATE TABLE IF NOT EXISTS ticket_sla_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    policy_id BIGINT NOT NULL COMMENT 'SLA 策略 ID',
    first_response_deadline DATETIME NULL COMMENT '首次响应截止时间',
    resolve_deadline DATETIME NULL COMMENT '解决截止时间',
    breached TINYINT NOT NULL DEFAULT 0 COMMENT '是否已违约',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ticket_sla_ticket_id (ticket_id),
    KEY idx_ticket_sla_policy_id (policy_id)
) COMMENT='P1 草案：工单 SLA 实例';

-- P1: assignment rule draft.
CREATE TABLE IF NOT EXISTS ticket_assignment_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_name VARCHAR(128) NOT NULL COMMENT '分派规则名称',
    category VARCHAR(64) NULL COMMENT '适用分类',
    priority VARCHAR(64) NULL COMMENT '适用优先级',
    target_group_id BIGINT NULL COMMENT '目标工单组 ID',
    target_queue_id BIGINT NULL COMMENT '目标队列 ID',
    target_user_id BIGINT NULL COMMENT '目标处理人 ID',
    weight INT NOT NULL DEFAULT 0 COMMENT '规则权重',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ticket_assignment_rule_weight (weight)
) COMMENT='P1 草案：自动分派规则';

-- P2: approval and collaboration draft.
CREATE TABLE IF NOT EXISTS ticket_approval_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL COMMENT '工单 ID',
    approver_user_id BIGINT NOT NULL COMMENT '审批人用户 ID',
    approval_status VARCHAR(32) NOT NULL COMMENT '审批状态',
    approval_comment TEXT NULL COMMENT '审批意见',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ticket_approval_ticket_id (ticket_id),
    KEY idx_ticket_approval_approver (approver_user_id)
) COMMENT='P2 草案：工单审批任务';

CREATE TABLE IF NOT EXISTS ticket_subtask (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_ticket_id BIGINT NOT NULL COMMENT '父工单 ID',
    title VARCHAR(200) NOT NULL COMMENT '子任务标题',
    assignee_id BIGINT NULL COMMENT '子任务处理人',
    status VARCHAR(32) NOT NULL COMMENT '子任务状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ticket_subtask_parent (parent_ticket_id),
    KEY idx_ticket_subtask_assignee (assignee_id)
) COMMENT='P2 草案：工单子任务';

-- P2: knowledge governance draft.
CREATE TABLE IF NOT EXISTS ticket_knowledge_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL COMMENT '知识 ID',
    reviewer_user_id BIGINT NULL COMMENT '审核人用户 ID',
    review_status VARCHAR(32) NOT NULL COMMENT '审核状态',
    review_comment TEXT NULL COMMENT '审核意见',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_knowledge_review_knowledge_id (knowledge_id)
) COMMENT='P2 草案：知识审核';

CREATE TABLE IF NOT EXISTS ticket_knowledge_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL COMMENT '知识 ID',
    version_no INT NOT NULL COMMENT '版本号',
    content MEDIUMTEXT NOT NULL COMMENT '版本正文',
    content_summary VARCHAR(500) NULL COMMENT '版本摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_version (knowledge_id, version_no)
) COMMENT='P2 草案：知识版本';

-- P2: tenant and metrics draft.
CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',
    tenant_name VARCHAR(128) NOT NULL COMMENT '租户名称',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_code)
) COMMENT='P2 草案：租户';

CREATE TABLE IF NOT EXISTS ticket_metric_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    metric_date DATE NOT NULL COMMENT '统计日期',
    metric_key VARCHAR(128) NOT NULL COMMENT '指标编码',
    metric_value DECIMAL(18, 4) NOT NULL COMMENT '指标值',
    dimension_json JSON NULL COMMENT '维度 JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ticket_metric_daily (metric_date, metric_key)
) COMMENT='P2 草案：工单日指标';
