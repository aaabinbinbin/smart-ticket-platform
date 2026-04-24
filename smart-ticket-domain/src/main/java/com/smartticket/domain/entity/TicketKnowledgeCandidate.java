package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识候选实体，用于承载未自动准入、需要人工审核的工单知识。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketKnowledgeCandidate {
    /**
     * 候选主键 ID。
     */
    private Long id;

    /**
     * 来源工单 ID。
     */
    private Long ticketId;

    /**
     * 候选状态，例如 MANUAL_REVIEW、MANUAL_APPROVED、MANUAL_REJECTED。
     */
    private String status;

    /**
     * 知识质量评分。
     */
    private Integer qualityScore;

    /**
     * 准入或审核决策。
     */
    private String decision;

    /**
     * 进入候选或被拒绝的原因说明。
     */
    private String reason;

    /**
     * 人工审核备注。
     */
    private String reviewComment;

    /**
     * 敏感信息或安全风险说明。
     */
    private String sensitiveRisk;

    /**
     * 人工审核时间。
     */
    private LocalDateTime reviewedAt;

    /**
     * 审核人用户 ID。
     */
    private Long reviewedBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
