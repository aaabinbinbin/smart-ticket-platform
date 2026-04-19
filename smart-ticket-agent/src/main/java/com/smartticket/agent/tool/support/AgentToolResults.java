package com.smartticket.agent.tool.support;

import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent Tool 结果构造器。
 */
public final class AgentToolResults {
    private AgentToolResults() {
    }

    /** 构造不需要更新会话实体指针的成功结果。 */
    public static AgentToolResult success(String toolName, String reply, Object data) {
        return AgentToolResult.builder()
                .invoked(true)
                .status(AgentToolStatus.SUCCESS)
                .toolName(toolName)
                .reply(reply)
                .data(data)
                .build();
    }

    /** 构造会更新当前工单或处理人上下文的成功结果。 */
    public static AgentToolResult success(
            String toolName,
            String reply,
            Object data,
            Long activeTicketId,
            Long activeAssigneeId
    ) {
        return AgentToolResult.builder()
                .invoked(true)
                .status(AgentToolStatus.SUCCESS)
                .toolName(toolName)
                .reply(reply)
                .data(data)
                .activeTicketId(activeTicketId)
                .activeAssigneeId(activeAssigneeId)
                .build();
    }

    /** 构造统一缺参结果，便于上层继续保持 /api/agent/chat 响应兼容。 */
    public static AgentToolResult needMoreInfo(String toolName, List<AgentToolParameterField> missingFields) {
        return AgentToolResult.builder()
                .invoked(false)
                .status(AgentToolStatus.NEED_MORE_INFO)
                .toolName(toolName)
                .reply(buildMissingFieldsReply(missingFields))
                .data(missingFields)
                .build();
    }

    /** 构造 Tool 层可恢复失败结果；业务异常仍优先由全局异常处理兜底。 */
    public static AgentToolResult failed(String toolName, String reply, Object data) {
        return AgentToolResult.builder()
                .invoked(false)
                .status(AgentToolStatus.FAILED)
                .toolName(toolName)
                .reply(reply)
                .data(data)
                .build();
    }

    private static String buildMissingFieldsReply(List<AgentToolParameterField> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return "请补充必要信息。";
        }
        String fields = missingFields.stream()
                .map(AgentToolParameterField::getLabel)
                .collect(Collectors.joining("、"));
        return "请补充" + fields + "。";
    }
}
