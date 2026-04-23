package com.smartticket.agent.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.agent.model.IntentRoute;
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

@Service
public class AgentTraceService {
    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);
    private final Map<String, List<AgentTraceRecord>> bySession = new ConcurrentHashMap<>();
    private final Map<Long, List<AgentTraceRecord>> byUser = new ConcurrentHashMap<>();
    private final ObjectProvider<AgentTraceRecordMapper> traceRecordMapperProvider;
    private final ObjectMapper objectMapper;

    public AgentTraceService(ObjectProvider<AgentTraceRecordMapper> traceRecordMapperProvider, ObjectMapper objectMapper) {
        this.traceRecordMapperProvider = traceRecordMapperProvider;
        this.objectMapper = objectMapper;
    }

    public AgentTraceContext start(CurrentUser user, String sessionId, String rawInput) {
        return new AgentTraceContext(UUID.randomUUID().toString(), sessionId, user.getUserId(), rawInput);
    }

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
        if (context == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - context.getStartedAtMillis();
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
                .status(toolResult == null || toolResult.getStatus() == null ? "UNKNOWN" : toolResult.getStatus().name())
                .failureType(fallbackUsed ? "FALLBACK" : null)
                .stepJson(toStepJson(context.getSteps()))
                .createdAt(LocalDateTime.now())
                .build();
        persist(record);
        bySession.computeIfAbsent(record.getSessionId(), ignored -> new ArrayList<>()).add(record);
        byUser.computeIfAbsent(record.getUserId(), ignored -> new ArrayList<>()).add(record);
        log.info("agent trace: traceId={}, sessionId={}, userId={}, intent={}, stage={}, status={}, elapsedMs={}",
                record.getTraceId(), record.getSessionId(), record.getUserId(), record.getIntent(), record.getPlanStage(),
                record.getStatus(), record.getElapsedMillis());
    }

    public List<AgentTraceRecord> findBySessionId(String sessionId) {
        AgentTraceRecordMapper mapper = traceRecordMapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                List<AgentTraceRecord> records = mapper.findBySessionId(sessionId);
                if (!records.isEmpty()) {
                    return records;
                }
            } catch (RuntimeException ex) {
                log.warn("query agent trace by session from database failed: sessionId={}, reason={}", sessionId, ex.getMessage());
            }
        }
        return List.copyOf(bySession.getOrDefault(sessionId, List.of()));
    }

    public List<AgentTraceRecord> findRecentByUserId(Long userId, int limit) {
        AgentTraceRecordMapper mapper = traceRecordMapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                List<AgentTraceRecord> records = mapper.findRecentByUserId(userId, limit);
                if (!records.isEmpty()) {
                    return records;
                }
            } catch (RuntimeException ex) {
                log.warn("query recent agent trace from database failed: userId={}, reason={}", userId, ex.getMessage());
            }
        }
        return byUser.getOrDefault(userId, List.of()).stream()
                .sorted(Comparator.comparing(AgentTraceRecord::getCreatedAt).reversed())
                .limit(limit)
                .toList();
    }

    public List<AgentTraceRecord> findByFailureType(String failureType) {
        AgentTraceRecordMapper mapper = traceRecordMapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                List<AgentTraceRecord> records = mapper.findByFailureType(failureType);
                if (!records.isEmpty()) {
                    return records;
                }
            } catch (RuntimeException ex) {
                log.warn("query agent trace by failure type from database failed: failureType={}, reason={}", failureType, ex.getMessage());
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
            log.warn("persist agent trace failed, keep in memory: traceId={}, reason={}", record.getTraceId(), ex.getMessage());
        }
    }

    private String toStepJson(List<AgentTraceStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
