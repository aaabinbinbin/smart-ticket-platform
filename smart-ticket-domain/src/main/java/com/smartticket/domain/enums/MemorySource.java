package com.smartticket.domain.enums;

/**
 * 记忆来源类型。
 *
 * <p>用于标记记忆的来源，帮助判断记忆的可信度。
 * 优先级：USER_EXPLICIT > TOOL_RESULT > INFERRED > LLM_EXTRACTED</p>
 */
public enum MemorySource {
    /** 用户明确表达的偏好或信息，可信度最高。 */
    USER_EXPLICIT,
    /** 工具执行返回的结果，可信度较高。 */
    TOOL_RESULT,
    /** 系统规则推断的结果，可信度中等。 */
    INFERRED,
    /** LLM 从对话中抽取的信息，可信度最低。 */
    LLM_EXTRACTED
}
