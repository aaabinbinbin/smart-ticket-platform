package com.smartticket.agent.llm.model;

import com.smartticket.agent.model.AgentIntent;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 生成的受控工具调用计划。
 *
 * <p>该对象只表示模型建议，不代表系统已经执行工具。进入 Tool 前必须由编排器校验
 * intent、toolName、参数完整性、风险等级和确认要求。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmToolCallPlan {
    /**
     * 模型计划处理的业务意图。
     */
    private AgentIntent intent;

    /**
     * 模型建议调用的 Tool 名称。
     */
    private String toolName;

    /**
     * 模型为本次工具调用抽取的参数。
     */
    private LlmParameterExtractionResult parameters;

    /**
     * 模型判断当前是否需要继续向用户追问。
     */
    private Boolean needMoreInfo;

    /**
     * 模型认为缺失的字段名。最终缺参判断仍以 Tool 元数据校验为准。
     */
    @Builder.Default
    private List<String> missingFields = new ArrayList<>();

    /**
     * 模型建议的下一步动作，例如 EXECUTE_TOOL、ASK_CLARIFICATION、FINAL_RESPONSE。
     */
    private String nextAction;

    /**
     * 模型对计划的置信度。
     */
    private Double confidence;

    /**
     * 模型给出的计划理由，仅用于日志和排查。
     */
    private String reason;
}
