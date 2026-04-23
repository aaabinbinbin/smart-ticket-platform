package com.smartticket.agent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.service.IntentRouter;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class AgentEvalRunner {
    private final IntentRouter intentRouter;
    private final ObjectMapper objectMapper;

    public AgentEvalRunner(IntentRouter intentRouter, ObjectMapper objectMapper) {
        this.intentRouter = intentRouter;
        this.objectMapper = objectMapper;
    }

    public AgentEvalReport runSeedCases() {
        List<AgentEvalCase> cases = loadSeedCases();
        int routePassed = 0;
        int clarifyPassed = 0;
        for (AgentEvalCase evalCase : cases) {
            IntentRoute route = intentRouter.route(evalCase.getInput(), AgentSessionContext.builder().build());
            if (route.getIntent() == evalCase.getExpectedIntent()) {
                routePassed++;
            }
            if ((route.getConfidence() < 0.50d) == evalCase.isExpectedNeedClarify()) {
                clarifyPassed++;
            }
        }
        int total = cases.size();
        return AgentEvalReport.builder()
                .total(total)
                .routePassed(routePassed)
                .clarifyPassed(clarifyPassed)
                .routeAccuracy(total == 0 ? 0 : (double) routePassed / total)
                .clarifyAccuracy(total == 0 ? 0 : (double) clarifyPassed / total)
                .build();
    }

    private List<AgentEvalCase> loadSeedCases() {
        try {
            ClassPathResource resource = new ClassPathResource("agent/eval/seed-cases.json");
            try (InputStream input = resource.getInputStream()) {
                return objectMapper.readValue(input, new TypeReference<>() {
                });
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load agent eval seed cases", ex);
        }
    }
}
