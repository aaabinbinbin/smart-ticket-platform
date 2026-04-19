package com.smartticket.agent.tool.core;

import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Tool 元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolMetadata {
    private String name;
    private String description;
    private ToolRiskLevel riskLevel;
    private boolean readOnly;
    private boolean requireConfirmation;
    @Builder.Default
    private List<AgentToolParameterField> requiredFields = new ArrayList<>();
}
