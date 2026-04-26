package com.smartticket.agent.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.entity.AgentTraceRecord;
import com.smartticket.domain.mapper.AgentTraceRecordMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 智能体轨迹服务。
 */
@Service
public class AgentTraceService {
    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);
    private static final int MAX_IN_MEMORY_TRACES = 1000;
    private final Map<String, List<AgentTraceRecord>> bySession = new ConcurrentHashMap<>();
    private final Map<Long, List<AgentTraceRecord>> byUser = new ConcurrentHashMap<>();
    // 轨迹Record映射接口提供器
    private final ObjectProvider<AgentTraceRecordMapper> traceRecordMapperProvider;
    // object映射接口
    private final ObjectMapper objectMapper;

    /**
     * 构造智能体轨迹服务。
     */
    public AgentTraceService(ObjectProvider<AgentTraceRecordMapper> traceRecordMapperProvider, ObjectMapper objectMapper) {
        this.traceRecordMapperProvider = traceRecordMapperProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理开始。
     */
    public AgentTraceContext start(CurrentUser user, String sessionId, String rawInput) {
        return new AgentTraceContext(UUID.randomUUID().toString(), sessionId, user.getUserId(), rawInput);
    }

    /**
     * 处理步骤。
     */
    public void step(AgentTraceContext context, String stage, String action, String skillOrTool, String status, String detail) {
        if (context == null) {
            return;
        }
        context.getSteps().add(AgentTraceStep.builder()
                .stage(stage)
                .action(action)
                .skillOrTool(skillOrTool)
                .status(status)
                .detail(detail)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /**
     * 添加推理链记录到轨迹上下文。
     */
    public void recordReasoning(AgentTraceContext context, String reasoningSegment) {
        if (context == null || reasoningSegment == null || reasoningSegment.isBlank()) {
            return;
        }
        context.getReasoningChain().add(reasoningSegment.trim());
        context.getSteps().add(AgentTraceStep.builder()
                .stage("agent")
                .action("reasoning")
                .skillOrTool(null)
                .status("THOUGHT")
                .detail(limit(reasoningSegment.trim(), 500))
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /**
     * 处理finish。
     */
    public void finish(
            AgentTraceContext context,
            IntentRoute route,
            AgentPlan plan,
            AgentToolParameters parameters,
            AgentToolResult toolResult,
            String finalReply,
            boolean springAiUsed,
            boolean fallbackUsed
    ) {
        finish(context, route, plan, parameters, toolResult, finalReply, springAiUsed, fallbackUsed, null, null);
    }

    /**
     * 基于统一 summary 收尾本轮 trace。
     *
     * <p>P6 开始高压治理会在 summary 中记录 degraded、failureCode 和 failureReason。
     * 该方法只把这些结构化事实写入 trace，不影响主业务成功失败，也不会修改
     * session、memory 或 pendingAction。</p>
     *
     * @param context trace 上下文
     * @param route 当前意图路由
     * @param plan 当前执行计划
     * @param summary 本轮统一执行摘要
     */
    public void finish(
            AgentTraceContext context,
            IntentRoute route,
            AgentPlan plan,
            AgentExecutionSummary summary
    ) {
        if (summary == null) {
            finish(context, route, plan, null, null, null, false, false, null, null);
            return;
        }
        finish(
                context,
                route,
                plan,
                summary.getParameters(),
                summary.getPrimaryResult(),
                summary.getRenderedReply(),
                summary.isSpringAiUsed(),
                summary.isFallbackUsed(),
                summary.getFailureCode(),
                summary.getFailureReason()
        );
    }

    private void finish(
            AgentTraceContext context,
            IntentRoute route,
            AgentPlan plan,
            AgentToolParameters parameters,
            AgentToolResult toolResult,
            String finalReply,
            boolean springAiUsed,
            boolean fallbackUsed,
            String failureCode,
            String failureReason
    ) {
        if (context == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - context.getStartedAtMillis();
        String status = toolResult == null || toolResult.getStatus() == null ? "UNKNOWN" : toolResult.getStatus().name();
        AgentTraceRecord record = AgentTraceRecord.builder()
                .traceId(context.getTraceId())
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .rawInput(context.getRawInput())
                .intent(route == null ? null : route.getIntent().name())
                .confidence(route == null ? null : route.getConfidence())
                .planStage(plan == null || plan.getCurrentStage() == null ? null : plan.getCurrentStage().name())
                .triggeredSkill(plan == null ? null : plan.getNextSkillCode())
                .parameterSummary(parameters == null ? null : parameters.toString())
                .promptVersion(context.getPromptVersion())
                .springAiUsed(springAiUsed)
                .fallbackUsed(fallbackUsed)
                .finalReply(finalReply)
                .elapsedMillis(elapsed)
                .status(status)
                .failureType(failureCode != null ? failureCode : (fallbackUsed ? "FALLBACK" : null))
                .stepJson(toStepJson(context.getSteps()))
                .reasoningJson(toReasoningJson(context.getReasoningChain()))
                .inputTokens(context.getInputTokens())
                .outputTokens(context.getOutputTokens())
                .createdAt(LocalDateTime.now())
                .build();
        persist(record);
        // 内存缓存设上限，防止 OOM
        bySession.computeIfAbsent(record.getSessionId(), ignored -> new ArrayList<>()).add(record);
        byUser.computeIfAbsent(record.getUserId(), ignored -> new ArrayList<>()).add(record);
        if (bySession.size() > MAX_IN_MEMORY_TRACES || estimateTotalInMemory() > MAX_IN_MEMORY_TRACES) {
            trimInMemory();
        }
        log.info("智能体轨迹：traceId={}, sessionId={}, userId={}, intent={}, stage={}, status={}, elapsedMs={}",
                record.getTraceId(), record.getSessionId(), record.getUserId(), record.getIntent(), record.getPlanStage(),
                record.getStatus(), record.getElapsedMillis());
    }

    /**
     * 查询按会话ID。
     */
    public List<AgentTraceRecord> findBySessionId(String sessionId) {
        AgentTraceRecordMapper mapper = traceRecordMapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                List<AgentTraceRecord> records = mapper.findBySessionId(sessionId);
                if (!records.isEmpty()) {
                    return records;
                }
            } catch (RuntimeException ex) {
                log.warn("按会话查询智能体轨迹失败：sessionId={}, reason={}", sessionId, ex.getMessage());
            }
        }
        return List.copyOf(bySession.getOrDefault(sessionId, List.of()));
    }

    /**
     * 查询Recent按用户ID。
     */
    public List<AgentTraceRecord> findRecentByUserId(Long userId, int limit) {
        AgentTraceRecordMapper mapper = traceRecordMapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                List<AgentTraceRecord> records = mapper.findRecentByUserId(userId, limit);
                if (!records.isEmpty()) {
                    return records;
                }
            } catch (RuntimeException ex) {
                log.warn("查询最近智能体轨迹失败：userId={}, reason={}", userId, ex.getMessage());
            }
        }
        return byUser.getOrDefault(userId, List.of()).stream()
                .sorted(Comparator.comparing(AgentTraceRecord::getCreatedAt).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * 按失败类型查询。
     */
    public List<AgentTraceRecord> findByFailureType(String failureType) {
        AgentTraceRecordMapper mapper = traceRecordMapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                List<AgentTraceRecord> records = mapper.findByFailureType(failureType);
                if (!records.isEmpty()) {
                    return records;
                }
            } catch (RuntimeException ex) {
                log.warn("按失败类型查询智能体轨迹失败：failureType={}, reason={}", failureType, ex.getMessage());
            }
        }
        return bySession.values().stream()
                .flatMap(List::stream)
                .filter(record -> failureType == null || failureType.equalsIgnoreCase(record.getFailureType()))
                .toList();
    }

    private void persist(AgentTraceRecord record) {
        AgentTraceRecordMapper mapper = traceRecordMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            mapper.insert(record);
        } catch (RuntimeException ex) {
            log.warn("持久化智能体轨迹失败，改为仅保留内存记录：traceId={}, reason={}", record.getTraceId(), ex.getMessage());
        }
    }

    /**
     * 转换为步骤JSON。
     */
    private String toStepJson(List<AgentTraceStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    /**
     * 将推理链转换为 JSON 数组字符串。
     */
    private String toReasoningJson(List<String> reasoningChain) {
        if (reasoningChain == null || reasoningChain.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(reasoningChain);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private int estimateTotalInMemory() {
        return bySession.values().stream().mapToInt(List::size).sum();
    }

    private void trimInMemory() {
        bySession.values().forEach(list -> {
            while (list.size() > 200) {
                list.remove(0);
            }
        });
        byUser.values().forEach(list -> {
            while (list.size() > 200) {
                list.remove(0);
            }
        });
        if (bySession.size() > MAX_IN_MEMORY_TRACES) {
            var keys = List.copyOf(bySession.keySet());
            for (int i = 0; i < keys.size() - MAX_IN_MEMORY_TRACES / 2 && i < keys.size(); i++) {
                bySession.remove(keys.get(i));
            }
        }
        if (byUser.size() > MAX_IN_MEMORY_TRACES) {
            var keys = List.copyOf(byUser.keySet());
            for (int i = 0; i < keys.size() - MAX_IN_MEMORY_TRACES / 2 && i < keys.size(); i++) {
                byUser.remove(keys.get(i));
            }
        }
    }
}
