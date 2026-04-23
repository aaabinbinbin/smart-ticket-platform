package com.smartticket.agent.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanStage;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.entity.AgentTraceRecord;
import com.smartticket.domain.mapper.AgentTraceRecordMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AgentTraceServiceTest {

    @Test
    void finishShouldPersistAndKeepMemoryFallback() {
        AgentTraceRecordMapper mapper = mock(AgentTraceRecordMapper.class);
        ObjectProvider<AgentTraceRecordMapper> provider = provider(mapper);
        AgentTraceService service = new AgentTraceService(provider, new ObjectMapper());
        AgentTraceContext context = service.start(CurrentUser.builder().userId(1L).username("u1").build(), "s1", "查询工单");
        context.setPromptVersion("result-explanation:v1");
        service.step(context, "route", "after", null, "QUERY_TICKET", "0.9");

        service.finish(
                context,
                IntentRoute.builder().intent(AgentIntent.QUERY_TICKET).confidence(0.9d).reason("查询").build(),
                AgentPlan.builder().nextSkillCode("query-ticket").currentStage(AgentPlanStage.SUMMARIZE_RESULT).build(),
                null,
                AgentToolResult.builder().status(AgentToolStatus.SUCCESS).toolName("queryTicket").reply("完成").build(),
                "完成",
                false,
                false
        );

        verify(mapper).insert(any(AgentTraceRecord.class));
        assertEquals(1, service.findBySessionId("s1").size());
        assertEquals("result-explanation:v1", service.findBySessionId("s1").get(0).getPromptVersion());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<AgentTraceRecordMapper> provider(AgentTraceRecordMapper mapper) {
        ObjectProvider<AgentTraceRecordMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        when(mapper.findBySessionId("s1")).thenReturn(List.of());
        return provider;
    }
}
