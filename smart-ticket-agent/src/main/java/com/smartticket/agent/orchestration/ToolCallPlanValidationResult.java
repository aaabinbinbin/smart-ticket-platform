package com.smartticket.agent.orchestration;

import com.smartticket.agent.tool.core.AgentTool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用计划校验结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallPlanValidationResult {
    /**
     * 计划是否通过校验。
     */
    private boolean valid;

    /**
     * 校验通过后解析出的 Tool。
     */
    private AgentTool tool;

    /**
     * 是否需要用户确认后才能执行。
     */
    private boolean needConfirmation;

    /**
     * 校验失败或需要确认时的说明。
     */
    private String reason;

    public static ToolCallPlanValidationResult valid(AgentTool tool) {
        return ToolCallPlanValidationResult.builder()
                .valid(true)
                .tool(tool)
                .build();
    }

    public static ToolCallPlanValidationResult needConfirmation(AgentTool tool, String reason) {
        return ToolCallPlanValidationResult.builder()
                .valid(true)
                .tool(tool)
                .needConfirmation(true)
                .reason(reason)
                .build();
    }

    public static ToolCallPlanValidationResult invalid(String reason) {
        return ToolCallPlanValidationResult.builder()
                .valid(false)
                .reason(reason)
                .build();
    }
}
