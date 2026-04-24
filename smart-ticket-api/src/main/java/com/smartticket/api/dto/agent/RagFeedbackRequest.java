package com.smartticket.api.dto.agent;

import com.smartticket.rag.service.RagFeedbackType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 检索增强反馈请求对象。
 */
@Data
public class RagFeedbackRequest {
    // 知识ID
    @NotNull
    private Long knowledgeId;
    // 工单ID
    private Long ticketId;
    // 会话ID
    private String sessionId;
    // 查询Text
    private String queryText;
    // 反馈类型
    @NotNull
    private RagFeedbackType feedbackType;
    // 评论
    private String comment;
}
