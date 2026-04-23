package com.smartticket.agent.memory;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.AgentTicketDomainMemory;
import com.smartticket.agent.model.AgentWorkingMemory;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.entity.AgentUserPreferenceMemory;
import com.smartticket.domain.mapper.AgentUserPreferenceMemoryMapper;
import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AgentMemoryService {
    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);
    private static final Duration TICKET_MEMORY_TTL = Duration.ofMinutes(30);

    private final ObjectProvider<AgentUserPreferenceMemoryMapper> preferenceMapperProvider;
    private final ObjectProvider<RedisJsonClient> redisJsonClientProvider;

    public AgentMemoryService(
            ObjectProvider<AgentUserPreferenceMemoryMapper> preferenceMapperProvider,
            ObjectProvider<RedisJsonClient> redisJsonClientProvider
    ) {
        this.preferenceMapperProvider = preferenceMapperProvider;
        this.redisJsonClientProvider = redisJsonClientProvider;
    }

    public void hydrate(CurrentUser currentUser, AgentSessionContext context) {
        if (context == null) {
            return;
        }
        loadUserPreference(currentUser, context);
        loadTicketDomainMemory(context);
    }

    public void remember(
            CurrentUser currentUser,
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters parameters,
            AgentToolResult toolResult
    ) {
        if (context == null || toolResult == null) {
            return;
        }
        context.setWorkingMemory(AgentWorkingMemory.from(
                context.getWorkingMemory(),
                route,
                parameters,
                toolResult.getToolName(),
                toolResult.getStatus() == null ? null : toolResult.getStatus().name(),
                toolResult.getReply()
        ));
        rememberUserPreference(currentUser, context, parameters);
        rememberTicketDomain(context, toolResult);
    }

    private void loadUserPreference(CurrentUser currentUser, AgentSessionContext context) {
        if (currentUser == null || currentUser.getUserId() == null) {
            return;
        }
        AgentUserPreferenceMemoryMapper mapper = preferenceMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            context.setUserPreferenceMemory(mapper.findByUserId(currentUser.getUserId()));
        } catch (RuntimeException ex) {
            log.debug("load agent user preference memory failed, userId={}", currentUser.getUserId(), ex);
        }
    }

    private void loadTicketDomainMemory(AgentSessionContext context) {
        if (context.getActiveTicketId() == null) {
            return;
        }
        RedisJsonClient redisJsonClient = redisJsonClientProvider.getIfAvailable();
        if (redisJsonClient == null) {
            return;
        }
        try {
            context.setTicketDomainMemory(redisJsonClient.get(
                    RedisKeys.agentTicketDomainMemory(context.getActiveTicketId()),
                    AgentTicketDomainMemory.class
            ));
        } catch (RuntimeException ex) {
            log.debug("load agent ticket domain memory failed, ticketId={}", context.getActiveTicketId(), ex);
        }
    }

    private void rememberUserPreference(
            CurrentUser currentUser,
            AgentSessionContext context,
            AgentToolParameters parameters
    ) {
        if (currentUser == null || currentUser.getUserId() == null || parameters == null) {
            return;
        }
        AgentUserPreferenceMemoryMapper mapper = preferenceMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        AgentUserPreferenceMemory memory = mergePreference(currentUser.getUserId(), context.getUserPreferenceMemory(), parameters);
        try {
            mapper.upsert(memory);
            context.setUserPreferenceMemory(memory);
        } catch (RuntimeException ex) {
            log.debug("persist agent user preference memory failed, userId={}", currentUser.getUserId(), ex);
        }
    }

    private AgentUserPreferenceMemory mergePreference(
            Long userId,
            AgentUserPreferenceMemory existing,
            AgentToolParameters parameters
    ) {
        AgentUserPreferenceMemory memory = existing == null
                ? AgentUserPreferenceMemory.builder().userId(userId).build()
                : existing;
        if (parameters.getType() != null) {
            memory.setCommonTicketType(parameters.getType().name());
        }
        if (parameters.getCategory() != null) {
            memory.setCommonCategory(parameters.getCategory().name());
        }
        if (parameters.getPriority() != null) {
            memory.setCommonPriority(parameters.getPriority().name());
        }
        if (memory.getResponseStyle() == null) {
            memory.setResponseStyle("CONCISE");
        }
        memory.setUpdatedAt(LocalDateTime.now());
        return memory;
    }

    private void rememberTicketDomain(AgentSessionContext context, AgentToolResult toolResult) {
        Long ticketId = toolResult.getActiveTicketId() == null
                ? context.getActiveTicketId()
                : toolResult.getActiveTicketId();
        if (ticketId == null) {
            return;
        }
        AgentTicketDomainMemory memory = AgentTicketDomainMemory.builder()
                .ticketId(ticketId)
                .latestEventSummary(limit(toolResult.getReply(), 800))
                .riskStatus(riskStatus(toolResult.getReply()))
                .approvalStatus(approvalStatus(toolResult.getReply()))
                .updatedAt(LocalDateTime.now())
                .build();
        context.setTicketDomainMemory(memory);

        RedisJsonClient redisJsonClient = redisJsonClientProvider.getIfAvailable();
        if (redisJsonClient == null) {
            return;
        }
        try {
            redisJsonClient.set(RedisKeys.agentTicketDomainMemory(ticketId), memory, TICKET_MEMORY_TTL);
        } catch (RuntimeException ex) {
            log.debug("persist agent ticket domain memory failed, ticketId={}", ticketId, ex);
        }
    }

    private String riskStatus(String text) {
        if (containsAny(text, "紧急", "高优先级", "URGENT", "HIGH", "风险", "失败")) {
            return "WATCH";
        }
        return "NORMAL";
    }

    private String approvalStatus(String text) {
        if (containsAny(text, "审批", "approval", "APPROVAL")) {
            return "MENTIONED";
        }
        return "UNKNOWN";
    }

    private boolean containsAny(String text, String... candidates) {
        if (text == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
