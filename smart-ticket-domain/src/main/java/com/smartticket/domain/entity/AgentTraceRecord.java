package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体执行轨迹记录，用于持久化一次调用的关键执行过程。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceRecord {
    /**
     * Trace 唯一 ID。
     */
    private String traceId;

    /**
     * Agent 会话 ID。
     */
    private String sessionId;

    /**
     * 发起调用的用户 ID。
     */
    private Long userId;

    /**
     * 用户原始输入。
     */
    private String rawInput;

    /**
     * 本轮识别出的业务意图。
     */
    private String intent;

    /**
     * 意图路由置信度。
     */
    private Double confidence;

    /**
     * 结束时计划所处阶段。
     */
    private String planStage;

    /**
     * 本轮触发的技能编码。
     */
    private String triggeredSkill;

    /**
     * Tool 参数摘要。
     */
    private String parameterSummary;

    /**
     * 本轮使用的 prompt 版本。
     */
    private String promptVersion;

    /**
     * 是否使用 Spring AI Tool Calling。
     */
    private boolean springAiUsed;

    /**
     * 是否进入确定性 fallback。
     */
    private boolean fallbackUsed;

    /**
     * 最终返回给用户的回复。
     */
    private String finalReply;

    /**
     * 本轮执行耗时，单位毫秒。
     */
    private long elapsedMillis;

    /**
     * 最终执行状态。
     */
    private String status;

    /**
     * 失败类型或 fallback 标记。
     */
    private String failureType;

    /**
     * 详细步骤 JSON。
     */
    private String stepJson;

    /**
     * LLM 推理链 JSON，记录 ReAct 循环中 LLM 中间思考过程。
     */
    private String reasoningJson;

    /**
     * 输入 token 数（prompt tokens），可为 null 表示未统计。
     */
    private Integer inputTokens;

    /**
     * 输出 token 数（completion tokens），可为 null 表示未统计。
     */
    private Integer outputTokens;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
