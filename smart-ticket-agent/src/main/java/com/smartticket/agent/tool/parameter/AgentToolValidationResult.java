package com.smartticket.agent.tool.parameter;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Tool 参数校验结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolValidationResult {
    private boolean valid;
    @Builder.Default
    private List<AgentToolParameterField> missingFields = new ArrayList<>();

    public static AgentToolValidationResult success() {
        return AgentToolValidationResult.builder()
                .valid(true)
                .build();
    }

    public static AgentToolValidationResult missing(List<AgentToolParameterField> missingFields) {
        return AgentToolValidationResult.builder()
                .valid(false)
                .missingFields(missingFields)
                .build();
    }
}
