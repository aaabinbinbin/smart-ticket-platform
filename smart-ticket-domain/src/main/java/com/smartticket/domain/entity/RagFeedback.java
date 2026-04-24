package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索增强反馈类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagFeedback {
    // ID
    private Long id;
    // 知识ID
    private Long knowledgeId;
    // 工单ID
    private Long ticketId;
    // 会话ID
    private String sessionId;
    // 查询Text
    private String queryText;
    // 反馈类型
    private String feedbackType;
    // 评论
    private String comment;
    // 创建按
    private Long createdBy;
    // 创建时间
    private LocalDateTime createdAt;
}
