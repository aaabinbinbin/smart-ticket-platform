-- =========================
-- v002: 为 Agent 记忆表增加可靠性元数据字段
-- 涉及表：agent_user_preference_memory
-- 新增字段：source, confidence, expires_at
-- =========================

ALTER TABLE agent_user_preference_memory
    ADD COLUMN source VARCHAR(32) DEFAULT NULL COMMENT '记忆来源：USER_EXPLICIT/TOOL_RESULT/INFERRED/LLM_EXTRACTED' AFTER response_style,
    ADD COLUMN confidence DECIMAL(3,2) DEFAULT NULL COMMENT '置信度(0-1)' AFTER source,
    ADD COLUMN expires_at DATETIME DEFAULT NULL COMMENT '过期时间' AFTER confidence;
