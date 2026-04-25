package com.smartticket.agent.execution;

/**
 * Agent 单轮执行模式。
 *
 * <p>该枚举用于标识当前结果来自哪一类执行路径，帮助 P1 阶段先把“结果模型”和“回复渲染”
 * 从 AgentFacade 中收敛出来。它只表达编排语义，不直接决定权限、风控或工具调用，
 * 也不会修改 session、memory、pendingAction 或 trace。</p>
 */
public enum AgentExecutionMode {
    /**
     * 意图置信度不足，需要先澄清用户目标。
     */
    CLARIFICATION,

    /**
     * 只读 ReAct 路径，通常由 Spring AI 参与多步查询与总结。
     */
    READ_ONLY_REACT,

    /**
     * 只读确定性路径，不依赖 LLM 的多步工具调用。
     */
    READ_ONLY_DETERMINISTIC,

    /**
     * 写操作参数尚未齐全，只生成命令草稿并等待补参。
     */
    WRITE_COMMAND_DRAFT,

    /**
     * 写操作通过确定性命令链路执行。
     */
    WRITE_COMMAND_EXECUTE,

    /**
     * 兼容 P1/P2 阶段保留的泛化写路径标记。
     *
     * <p>P3 开始新的写操作主链优先使用 {@link #WRITE_COMMAND_DRAFT} 和
     * {@link #WRITE_COMMAND_EXECUTE}，该枚举值仅用于尚未迁移的旧分支兼容。</p>
     */
    WRITE_DETERMINISTIC,

    /**
     * 高风险操作进入确认态。
     */
    HIGH_RISK_CONFIRMATION,

    /**
     * 当前消息是在继续处理上一轮 pendingAction。
     */
    PENDING_CONTINUATION,

    /**
     * 当前意图没有任何通过权限和风险过滤的可用 Skill。
     *
     * <p>该模式用于安全失败：不能暴露 toolName，不能自动执行，也不能再通过旧的 intent fallback
     * 绕过 SkillRegistry.findAvailable 的运行时过滤。</p>
     */
    SAFE_FAILURE
}
