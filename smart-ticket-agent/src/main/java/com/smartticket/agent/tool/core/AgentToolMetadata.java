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
    // name
    private String name;
    // 描述
    private String description;
    // riskLevel
    private ToolRiskLevel riskLevel;
    // readOnly
    private boolean readOnly;
    // requireConfirmation
    private boolean requireConfirmation;
    @Builder.Default
    private List<AgentToolParameterField> requiredFields = new ArrayList<>();
}
