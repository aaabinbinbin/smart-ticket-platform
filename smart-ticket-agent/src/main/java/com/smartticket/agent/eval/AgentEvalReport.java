package com.smartticket.agent.eval;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentEvalReport {
    private int total;
    private int routePassed;
    private int clarifyPassed;
    private double routeAccuracy;
    private double clarifyAccuracy;
}
