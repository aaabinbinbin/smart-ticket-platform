package com.smartticket.agent.execution;

import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 执行前置决策结果。
 *
 * <p>Guard 会把 Tool 是否存在、风险确认、必填参数等判断归一成该对象。编排层只需要根据状态决定
 * 是否执行 Tool、追问用户或直接失败。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionDecision {
    /** 当前决策状态。 */
    private AgentExecutionDecisionStatus status;

    /** 校验通过后解析出的 Tool；只有计划合法时才可能存在。 */
    private AgentTool tool;

    /** 决策原因，用于日志和用户回复兜底。 */
    private String reason;

    /** 缺少的 Tool 参数字段，仅 NEED_MORE_INFO 场景使用。 */
    @Builder.Default
    private List<AgentToolParameterField> missingFields = new ArrayList<>();

    /** 判断是否可以直接执行 Tool。 */
    public boolean isAllowed() {
        return status == AgentExecutionDecisionStatus.ALLOW_EXECUTE && tool != null;
    }

    /** 判断是否为计划级拒绝。 */
    public boolean isRejected() {
        return status == AgentExecutionDecisionStatus.REJECTED;
    }

    /** 判断该决策是否只需要返回回复，不应执行 Tool。 */
    public boolean replyOnly() {
        return status == AgentExecutionDecisionStatus.NEED_CONFIRMATION
                || status == AgentExecutionDecisionStatus.NEED_MORE_INFO
                || status == AgentExecutionDecisionStatus.REJECTED;
    }

    /** 把非执行决策转换为统一 ToolResult，供后续回复生成和上下文更新复用。 */
    public AgentToolResult toToolResult(String fallbackToolName) {
        String toolName = tool == null ? fallbackToolName : tool.name();
        if (status == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            return AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.NEED_MORE_INFO)
                    .toolName(toolName)
                    .reply(reason)
                    .data(List.of())
                    .build();
        }
        if (status == AgentExecutionDecisionStatus.NEED_MORE_INFO) {
            return AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.NEED_MORE_INFO)
                    .toolName(toolName)
                    .reply("请补充必要信息。")
                    .data(missingFields == null ? List.of() : missingFields)
                    .build();
        }
        return AgentToolResult.builder()
                .invoked(false)
                .status(AgentToolStatus.FAILED)
                .toolName(toolName == null ? "agentExecutionGuard" : toolName)
                .reply("当前请求无法匹配到可安全执行的工具，请调整描述后重试。")
                .data(reason)
                .build();
    }

    /** 构造允许执行的决策。 */
    public static AgentExecutionDecision allow(AgentTool tool) {
        return AgentExecutionDecision.builder()
                .status(AgentExecutionDecisionStatus.ALLOW_EXECUTE)
                .tool(tool)
                .build();
    }

    /** 构造需要用户确认的决策。 */
    public static AgentExecutionDecision needConfirmation(AgentTool tool, String reason) {
        return AgentExecutionDecision.builder()
                .status(AgentExecutionDecisionStatus.NEED_CONFIRMATION)
                .tool(tool)
                .reason(reason)
                .build();
    }

    /** 构造需要补充参数的决策。 */
    public static AgentExecutionDecision needMoreInfo(AgentTool tool, List<AgentToolParameterField> missingFields) {
        return AgentExecutionDecision.builder()
                .status(AgentExecutionDecisionStatus.NEED_MORE_INFO)
                .tool(tool)
                .missingFields(missingFields == null ? List.of() : missingFields)
                .reason("缺少 Tool 必填参数")
                .build();
    }

    /** 构造拒绝执行的决策。 */
    public static AgentExecutionDecision rejected(String reason) {
        return AgentExecutionDecision.builder()
                .status(AgentExecutionDecisionStatus.REJECTED)
                .reason(reason)
                .build();
    }
}
