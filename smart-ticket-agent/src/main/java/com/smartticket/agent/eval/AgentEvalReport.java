package com.smartticket.agent.eval;

import lombok.Builder;
import lombok.Data;

/**
 * 智能体评测报告类。
 */
@Data
@Builder
public class AgentEvalReport {
    // total
    private int total;
    // routePassed
    private int routePassed;
    // clarifyPassed
    private int clarifyPassed;
    // routeAccuracy
    private double routeAccuracy;
    // clarifyAccuracy
    private double clarifyAccuracy;
}
