-- 企业智能工单协同平台数据库初始化脚本。
-- 当前脚本只负责创建 MySQL 主业务库和 MVP 阶段核心表。
-- RAG 向量字段后续接入 pgvector 时单独维护，不放在 MySQL 主库中。

-- 创建业务数据库，统一使用 utf8mb4 以支持中文、英文和符号内容。
CREATE DATABASE IF NOT EXISTS smart_ticket_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_ticket_platform;

-- 系统用户表。
-- 保存登录账号、密码摘要、展示姓名、邮箱和账号启停状态。
-- 这里只保存用户基础身份信息，不保存用户在某张工单中的业务位置。
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录用户名，全局唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值，不保存明文密码',
    real_name VARCHAR(64) NOT NULL COMMENT '用户真实姓名或展示名',
    email VARCHAR(128) COMMENT '邮箱地址',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：1-启用，0-禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 系统角色表。
-- 第一版固定使用 USER / STAFF / ADMIN 三类角色。
-- 角色只表示系统能力，不表示提单人、处理人这类工单内业务关系。
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色主键',
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码：USER/STAFF/ADMIN',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户角色关联表。
-- 一个用户可以拥有多个角色，例如处理人员同时拥有 USER 和 STAFF。
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工单主表。
-- 保存工单当前事实状态：当前状态、当前负责人、优先级、分类等。
-- 评论、操作日志、附件等过程数据拆分到独立表。
CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单主键',
    ticket_no VARCHAR(32) NOT NULL UNIQUE COMMENT '业务工单号，例如 INC202604170001',
    title VARCHAR(200) NOT NULL COMMENT '工单标题',
    description TEXT NOT NULL COMMENT '问题描述',
    category VARCHAR(64) NOT NULL COMMENT '工单分类：ACCOUNT/SYSTEM/ENVIRONMENT/OTHER',
    priority VARCHAR(32) NOT NULL COMMENT '优先级：LOW/MEDIUM/HIGH/URGENT',
    status VARCHAR(32) NOT NULL COMMENT '状态：PENDING_ASSIGN/PROCESSING/RESOLVED/CLOSED',
    creator_id BIGINT NOT NULL COMMENT '提单人用户 ID',
    assignee_id BIGINT DEFAULT NULL COMMENT '当前处理人用户 ID，待分配时可为空',
    solution_summary TEXT COMMENT '解决方案摘要，通常在解决或关闭阶段填写',
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '创建来源：MANUAL-手工创建，AGENT-Agent 创建',
    idempotency_key VARCHAR(128) DEFAULT NULL COMMENT '创建幂等键，用于防止重复提交',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_creator_id (creator_id),
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_status (status),
    INDEX idx_category (category),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工单评论表。
-- 保存用户回复、处理过程记录、解决方案补充等协作内容。
CREATE TABLE IF NOT EXISTS ticket_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论主键',
    ticket_id BIGINT NOT NULL COMMENT '所属工单 ID',
    commenter_id BIGINT NOT NULL COMMENT '评论人用户 ID',
    comment_type VARCHAR(32) NOT NULL COMMENT '评论类型：USER_REPLY/PROCESS_LOG/SOLUTION',
    content TEXT NOT NULL COMMENT '评论正文',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_commenter_id (commenter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工单操作日志表。
-- 保存创建、分配、转派、状态变更、评论、关闭等关键操作轨迹。
-- 该表服务于审计和追溯，不作为当前事实来源。
CREATE TABLE IF NOT EXISTS ticket_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志主键',
    ticket_id BIGINT NOT NULL COMMENT '所属工单 ID',
    operator_id BIGINT NOT NULL COMMENT '操作人用户 ID',
    operation_type VARCHAR(64) NOT NULL COMMENT '操作类型：CREATE/ASSIGN/TRANSFER/UPDATE_STATUS/COMMENT/CLOSE',
    operation_desc VARCHAR(500) NOT NULL COMMENT '操作说明',
    before_value TEXT COMMENT '变更前内容，可存 JSON 或文本',
    after_value TEXT COMMENT '变更后内容，可存 JSON 或文本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_operator_id (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工单附件表。
-- MVP 阶段只保存文件 URL，不在数据库中保存文件二进制内容。
CREATE TABLE IF NOT EXISTS ticket_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '附件主键',
    ticket_id BIGINT NOT NULL COMMENT '所属工单 ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_url VARCHAR(500) NOT NULL COMMENT '文件访问地址或对象存储地址',
    file_type VARCHAR(64) COMMENT '文件类型或扩展名',
    file_size BIGINT COMMENT '文件大小，单位字节',
    uploader_id BIGINT NOT NULL COMMENT '上传人用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工单知识表。
-- 保存已关闭工单沉淀出的知识文本，是后续 RAG 切片和检索的来源。
-- 该表属于知识数据，不参与工单主事务状态判断。
CREATE TABLE IF NOT EXISTS ticket_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识主键',
    ticket_id BIGINT NOT NULL COMMENT '来源工单 ID',
    content TEXT NOT NULL COMMENT '用于切片和向量化的知识正文',
    content_summary VARCHAR(1000) COMMENT '知识摘要，便于列表展示和召回结果展示',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '知识状态：ACTIVE-可用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工单知识切片表。
-- 保存知识文本切片和第一版向量 JSON。后续接入 pgvector 时可迁移到专用向量字段。
CREATE TABLE IF NOT EXISTS ticket_knowledge_embedding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识切片主键',
    knowledge_id BIGINT NOT NULL COMMENT '所属知识 ID',
    chunk_index INT NOT NULL COMMENT '切片序号，从 0 或 1 开始由 RAG 模块约定',
    chunk_text TEXT NOT NULL COMMENT '切片文本内容',
    embedding_vector TEXT COMMENT '向量 JSON 文本，第一版用于打通知识向量化入库链路',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
