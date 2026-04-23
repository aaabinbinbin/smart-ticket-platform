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
public class RagFeedback {
    private Long id;
    private Long knowledgeId;
    private Long ticketId;
    private String sessionId;
    private String queryText;
    private String feedbackType;
    private String comment;
    private Long createdBy;
    private LocalDateTime createdAt;
}
