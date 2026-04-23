package com.smartticket.api.dto.agent;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceMetricsResponse {
    private int total;
    private long clarifyCount;
    private long springAiUsedCount;
    private long springAiSuccessCount;
    private long fallbackCount;
    private Map<String, Long> routeDistribution;
    private Map<String, Long> skillUsage;
}
