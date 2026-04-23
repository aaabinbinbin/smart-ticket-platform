package com.smartticket.agent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.AgentTicketDomainMemory;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.entity.AgentUserPreferenceMemory;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import com.smartticket.domain.mapper.AgentUserPreferenceMemoryMapper;
import com.smartticket.infra.redis.RedisJsonClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AgentMemoryServiceTest {

    @Test
    void hydrateShouldLoadPreferenceAndTicketMemory() {
        AgentUserPreferenceMemoryMapper mapper = mock(AgentUserPreferenceMemoryMapper.class);
        RedisJsonClient redisJsonClient = mock(RedisJsonClient.class);
        AgentMemoryService service = new AgentMemoryService(provider(mapper), provider(redisJsonClient));
        AgentSessionContext context = AgentSessionContext.builder().activeTicketId(1001L).build();
        when(mapper.findByUserId(1L)).thenReturn(AgentUserPreferenceMemory.builder()
                .userId(1L)
                .commonCategory("SYSTEM")
                .build());
        when(redisJsonClient.get("agent:memory:ticket:1001", AgentTicketDomainMemory.class))
                .thenReturn(AgentTicketDomainMemory.builder().ticketId(1001L).riskStatus("WATCH").build());

        service.hydrate(currentUser(), context);

        assertEquals("SYSTEM", context.getUserPreferenceMemory().getCommonCategory());
        assertEquals("WATCH", context.getTicketDomainMemory().getRiskStatus());
    }

    @Test
    void rememberShouldUpdateThreeMemoryLayers() {
        AgentUserPreferenceMemoryMapper mapper = mock(AgentUserPreferenceMemoryMapper.class);
        RedisJsonClient redisJsonClient = mock(RedisJsonClient.class);
        AgentMemoryService service = new AgentMemoryService(provider(mapper), provider(redisJsonClient));
        AgentSessionContext context = AgentSessionContext.builder().activeTicketId(1002L).build();
        AgentToolParameters parameters = AgentToolParameters.builder()
                .type(TicketTypeEnum.INCIDENT)
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .build();

        service.remember(
                currentUser(),
                context,
                IntentRoute.builder().intent(AgentIntent.QUERY_TICKET).confidence(0.9d).build(),
                parameters,
                AgentToolResult.builder()
                        .toolName("queryTicket")
                        .status(AgentToolStatus.SUCCESS)
                        .activeTicketId(1002L)
                        .reply("高优先级工单已查询完成")
                        .build()
        );

        assertNotNull(context.getWorkingMemory());
        assertEquals("QUERY_TICKET", context.getWorkingMemory().getCurrentTaskStage());
        assertEquals("SYSTEM", context.getUserPreferenceMemory().getCommonCategory());
        assertEquals("WATCH", context.getTicketDomainMemory().getRiskStatus());
        verify(mapper).upsert(any(AgentUserPreferenceMemory.class));
        verify(redisJsonClient).set(eq("agent:memory:ticket:1002"), any(AgentTicketDomainMemory.class), any(Duration.class));
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
