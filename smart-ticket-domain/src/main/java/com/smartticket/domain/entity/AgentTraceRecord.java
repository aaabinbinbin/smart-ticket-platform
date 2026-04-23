package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceRecord {
    private String traceId;
    private String sessionId;
    private Long userId;
    private String rawInput;
    private String intent;
    private Double confidence;
    private String planStage;
    private String triggeredSkill;
    private String parameterSummary;
    private String promptVersion;
    private boolean springAiUsed;
    private boolean fallbackUsed;
    private String finalReply;
    private long elapsedMillis;
    private String status;
    private String failureType;
    private String stepJson;
    private LocalDateTime createdAt;
}
