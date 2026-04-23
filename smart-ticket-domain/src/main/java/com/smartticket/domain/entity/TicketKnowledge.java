package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单知识实体，对应表 {@code ticket_knowledge}。
 *
 * <p>该实体保存从已关闭工单中沉淀出的知识文本，作为后续 RAG 切片和检索的来源。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketKnowledge {
    /** 知识主键。 */
    private Long id;
    /** 来源工单 ID。 */
    private Long ticketId;
    /** 用于切片和向量化的知识正文。 */
    private String content;
    /** 知识摘要，便于检索结果展示。 */
    private String contentSummary;
    private String symptomSummary;
    private String rootCauseSummary;
    private String resolutionSteps;
    private String riskNotes;
    private String applicableScope;
    /** 知识状态，例如 ACTIVE。 */
    private String status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
