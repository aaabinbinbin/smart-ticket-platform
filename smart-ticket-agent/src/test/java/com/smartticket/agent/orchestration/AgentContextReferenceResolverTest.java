package com.smartticket.agent.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentContextReferenceResolverTest {
    private final AgentContextReferenceResolver resolver = new AgentContextReferenceResolver();

    @Test
    void applyReferencesShouldInheritTicketIdForTransferMessage() {
        AgentSessionContext context = AgentSessionContext.builder()
                .activeTicketId(1001L)
                .build();
        AgentToolParameters parameters = AgentToolParameters.builder()
                .numbers(List.of(3L))
                .build();

        resolver.applyReferences("把刚才那个工单转给3", context, parameters);

        assertEquals(1001L, parameters.getTicketId());
        assertEquals(3L, parameters.getAssigneeId());
    }
}
