package com.smartticket.api.dto.agent;

import com.smartticket.rag.service.RagFeedbackType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RagFeedbackRequest {
    @NotNull
    private Long knowledgeId;
    private Long ticketId;
    private String sessionId;
    private String queryText;
    @NotNull
    private RagFeedbackType feedbackType;
    private String comment;
}
