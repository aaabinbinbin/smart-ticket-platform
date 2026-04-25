package com.smartticket.agent.memory;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.AgentTicketDomainMemory;
import com.smartticket.agent.model.AgentWorkingMemory;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.entity.AgentUserPreferenceMemory;
import com.smartticket.domain.enums.MemorySource;
import com.smartticket.domain.mapper.AgentUserPreferenceMemoryMapper;
import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 智能体记忆服务。
 *
 * <p>记忆不是权威事实源，数据库/Tool 实时查询结果才是权威事实。
 * 记忆用于上下文缓存和偏好线索，不替代数据库查询。</p>
 *
 * <p>每条记忆包含来源(source)、置信度(confidence)和过期时间(expiresAt)，
 * 用于判断可信度。低置信度记忆只能用于推荐，不能自动执行。
 * 涉及工单状态、处理人、审批状态时必须实时查数据库。</p>
 */
@Service
public class AgentMemoryService {
    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);
    private static final Duration TICKET_MEMORY_TTL = Duration.ofMinutes(30);
    private static final double DEFAULT_CONFIDENCE = 0.85;
    private static final double INFERRED_CONFIDENCE = 0.60;

    // 用户偏好映射接口提供器
    private final ObjectProvider<AgentUserPreferenceMemoryMapper> preferenceMapperProvider;
    // Redis JSON 客户端提供器
    private final ObjectProvider<RedisJsonClient> redisJsonClientProvider;

    /**
     * 构造智能体记忆服务。
     */
    public AgentMemoryService(
            ObjectProvider<AgentUserPreferenceMemoryMapper> preferenceMapperProvider,
            ObjectProvider<RedisJsonClient> redisJsonClientProvider
    ) {
        this.preferenceMapperProvider = preferenceMapperProvider;
        this.redisJsonClientProvider = redisJsonClientProvider;
    }

    /**
     * 填充上下文。
     */
    public void hydrate(CurrentUser currentUser, AgentSessionContext context) {
        if (context == null) {
            return;
        }
        loadUserPreference(currentUser, context);
        loadTicketDomainMemory(context);
    }

    /**
     * 记录记忆。
     */
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

    /**
     * 加载用户偏好记忆。
     */
    private void loadUserPreference(CurrentUser currentUser, AgentSessionContext context) {
        if (currentUser == null || currentUser.getUserId() == null) {
            return;
        }
        AgentUserPreferenceMemoryMapper mapper = preferenceMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            AgentUserPreferenceMemory preference = mapper.findByUserId(currentUser.getUserId());
            // 跳过过期的偏好记忆
            if (preference != null && preference.getExpiresAt() != null
                    && preference.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.debug("skip expired user preference memory, userId={}", currentUser.getUserId());
                return;
            }
            context.setUserPreferenceMemory(preference);
        } catch (RuntimeException ex) {
            log.debug("load agent user preference memory failed, userId={}", currentUser.getUserId(), ex);
        }
    }

    /**
     * 加载工单领域记忆。
     */
    private void loadTicketDomainMemory(AgentSessionContext context) {
        if (context.getActiveTicketId() == null) {
            return;
        }
        RedisJsonClient redisJsonClient = redisJsonClientProvider.getIfAvailable();
        if (redisJsonClient == null) {
            return;
        }
        try {
            AgentTicketDomainMemory memory = redisJsonClient.get(
                    RedisKeys.agentTicketDomainMemory(context.getActiveTicketId()),
                    AgentTicketDomainMemory.class
            );
            // 跳过过期的工单领域记忆
            if (memory != null && memory.getExpiresAt() != null
                    && memory.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.debug("skip expired ticket domain memory, ticketId={}", context.getActiveTicketId());
                return;
            }
            context.setTicketDomainMemory(memory);
        } catch (RuntimeException ex) {
            log.debug("load agent ticket domain memory failed, ticketId={}", context.getActiveTicketId(), ex);
        }
    }

    /**
     * 记录用户偏好记忆。
     */
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

    /**
     * 合并新的用户偏好信息。
     */
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

        // 设置可靠性元数据
        if (parameters.getType() != null || parameters.getCategory() != null
                || parameters.getPriority() != null) {
            // 用户传入的参数来自当前交互，视为 TOOL_RESULT
            memory.setSource(MemorySource.TOOL_RESULT);
            memory.setConfidence(DEFAULT_CONFIDENCE);
            // 偏好记忆 7 天过期
            memory.setExpiresAt(LocalDateTime.now().plusDays(7));
        } else if (memory.getSource() == null) {
            // 没有新数据且没有已有来源标记：标记为 INFERRED
            memory.setSource(MemorySource.INFERRED);
            memory.setConfidence(INFERRED_CONFIDENCE);
            memory.setExpiresAt(LocalDateTime.now().plusDays(1));
        }

        memory.setUpdatedAt(LocalDateTime.now());
        return memory;
    }

    /**
     * 记录工单领域记忆。
     */
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
                // 来自工具执行结果的记忆
                .source(MemorySource.TOOL_RESULT)
                .confidence(DEFAULT_CONFIDENCE)
                .expiresAt(LocalDateTime.now().plus(TICKET_MEMORY_TTL))
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

    /**
     * 根据回复文本推断风险状态。
     */
    private String riskStatus(String text) {
        if (containsAny(text, "紧急", "高优先级", "URGENT", "HIGH", "风险", "失败")) {
            return "WATCH";
        }
        return "NORMAL";
    }

    /**
     * 根据回复文本推断审批状态。
     */
    private String approvalStatus(String text) {
        if (containsAny(text, "审批", "approval", "APPROVAL")) {
            return "MENTIONED";
        }
        return "UNKNOWN";
    }

    /**
     * 判断文本中是否包含任一候选词。
     */
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

    /**
     * 按最大长度截断文本。
     */
    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
