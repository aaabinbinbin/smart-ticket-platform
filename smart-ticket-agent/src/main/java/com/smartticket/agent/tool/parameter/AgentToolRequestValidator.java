package com.smartticket.agent.tool.parameter;

import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 基于 Tool 元数据的通用参数校验器。
 */
@Component
public class AgentToolRequestValidator {
    public AgentToolValidationResult validate(AgentTool tool, AgentToolRequest request) {
        // Tool 只声明静态必填字段；复杂业务规则仍交给具体 Tool 和 biz 层处理。
        List<AgentToolParameterField> missingFields = tool.metadata().getRequiredFields()
                .stream()
                .filter(field -> isMissing(field, request.getParameters()))
                .toList();
        if (missingFields.isEmpty()) {
            return AgentToolValidationResult.success();
        }
        return AgentToolValidationResult.missing(missingFields);
    }

    private boolean isMissing(AgentToolParameterField field, AgentToolParameters parameters) {
        if (parameters == null) {
            return true;
        }
        // 这里保持确定性空值判断，不做语义推断，避免和后续 LLM 参数抽取职责混在一起。
        return switch (field) {
            case TICKET_ID -> parameters.getTicketId() == null;
            case ASSIGNEE_ID -> parameters.getAssigneeId() == null;
            case TITLE -> parameters.getTitle() == null || parameters.getTitle().isBlank();
            case DESCRIPTION -> parameters.getDescription() == null || parameters.getDescription().isBlank();
            case CATEGORY -> parameters.getCategory() == null;
            case PRIORITY -> parameters.getPriority() == null;
        };
    }
}
