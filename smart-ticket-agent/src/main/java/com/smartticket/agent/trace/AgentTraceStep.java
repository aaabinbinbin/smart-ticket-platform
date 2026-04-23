package com.smartticket.agent.trace;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceStep {
    private String stage;
    private String action;
    private String skillOrTool;
    private String status;
    private String detail;
    private LocalDateTime occurredAt;
}
