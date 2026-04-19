package com.smartticket.biz.service;

import com.smartticket.biz.repository.TicketCommentRepository;
import com.smartticket.biz.repository.TicketKnowledgeRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.enums.CodeInfoEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单知识构建服务。
 *
 * <p>该服务负责判断工单是否具备知识沉淀条件、读取工单和评论、提取关键评论、
 * 生成标准化知识文本与摘要，并写入 ticket_knowledge。它不做向量化，也不做检索。</p>
 */
@Service
public class TicketKnowledgeService {
    private static final int MAX_KEY_COMMENTS = 8;
    private static final int MAX_SUMMARY_LENGTH = 900;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 工单仓储，用于读取工单事实数据。 */
    private final TicketRepository ticketRepository;

    /** 评论仓储，用于读取工单协作过程。 */
    private final TicketCommentRepository ticketCommentRepository;

    /** 知识仓储，用于写入或更新 ticket_knowledge。 */
    private final TicketKnowledgeRepository ticketKnowledgeRepository;

    public TicketKnowledgeService(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketKnowledgeRepository ticketKnowledgeRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.ticketKnowledgeRepository = ticketKnowledgeRepository;
    }

    /**
     * 判断工单是否具备知识沉淀条件。
     *
     * <p>第一版只沉淀已关闭且具备解决摘要或关键评论的工单，避免把未完成问题写入知识库。</p>
     */
    public boolean canBuildKnowledge(Ticket ticket, List<TicketComment> comments) {
        if (ticket == null || ticket.getStatus() != TicketStatusEnum.CLOSED) {
            return false;
        }
        return hasText(ticket.getSolutionSummary()) || !extractKeyComments(comments).isEmpty();
    }

    /**
     * 根据工单 ID 构建或更新知识记录。
     *
     * @param ticketId 来源工单 ID
     * @return 成功构建的知识记录；如果工单不满足沉淀条件则返回空
     */
    @Transactional
    public Optional<TicketKnowledge> buildKnowledge(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId);
        List<TicketComment> comments = ticketCommentRepository.findByTicketId(ticketId);
        if (!canBuildKnowledge(ticket, comments)) {
            return Optional.empty();
        }
        List<TicketComment> keyComments = extractKeyComments(comments);
        String content = buildContent(ticket, keyComments);
        String summary = buildSummary(ticket, keyComments);
        TicketKnowledge existing = ticketKnowledgeRepository.findByTicketId(ticketId);
        if (existing == null) {
            TicketKnowledge created = TicketKnowledge.builder()
                    .ticketId(ticketId)
                    .content(content)
                    .contentSummary(summary)
                    .status("ACTIVE")
                    .build();
            ticketKnowledgeRepository.insert(created);
            return Optional.of(created);
        }
        existing.setContent(content);
        existing.setContentSummary(summary);
        existing.setStatus("ACTIVE");
        ticketKnowledgeRepository.update(existing);
        return Optional.of(existing);
    }

    /**
     * 从评论中提取关键评论。
     *
     * <p>优先保留 SOLUTION 和 PROCESS_LOG 类型评论，再保留少量用户补充。第一版不做复杂 NLP 摘要。</p>
     */
    public List<TicketComment> extractKeyComments(List<TicketComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        return comments.stream()
                .filter(comment -> hasText(comment.getContent()))
                .sorted(Comparator
                        .comparingInt(this::commentPriority)
                        .thenComparing(TicketComment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_KEY_COMMENTS)
                .toList();
    }

    /** 生成标准化知识正文，供后续切片和向量化使用。 */
    private String buildContent(Ticket ticket, List<TicketComment> keyComments) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 工单知识\n\n");
        builder.append("## 基本信息\n");
        appendLine(builder, "工单ID", ticket.getId());
        appendLine(builder, "工单编号", ticket.getTicketNo());
        appendLine(builder, "标题", ticket.getTitle());
        appendLine(builder, "分类", enumCode(ticket.getCategory()));
        appendLine(builder, "优先级", enumCode(ticket.getPriority()));
        appendLine(builder, "状态", enumCode(ticket.getStatus()));
        appendLine(builder, "提单人ID", ticket.getCreatorId());
        appendLine(builder, "处理人ID", ticket.getAssigneeId());
        appendLine(builder, "创建时间", ticket.getCreatedAt() == null ? null : TIME_FORMATTER.format(ticket.getCreatedAt()));
        builder.append("\n## 问题描述\n");
        builder.append(nullToEmpty(ticket.getDescription())).append("\n");
        builder.append("\n## 解决摘要\n");
        builder.append(hasText(ticket.getSolutionSummary()) ? ticket.getSolutionSummary() : "暂无解决摘要").append("\n");
        builder.append("\n## 关键评论\n");
        if (keyComments.isEmpty()) {
            builder.append("暂无关键评论\n");
        } else {
            for (TicketComment comment : keyComments) {
                builder.append("- [")
                        .append(nullToEmpty(comment.getCommentType()))
                        .append("] ")
                        .append(nullToEmpty(comment.getContent()))
                        .append("\n");
            }
        }
        return builder.toString();
    }

    /** 生成知识摘要，便于列表展示和后续召回结果展示。 */
    private String buildSummary(Ticket ticket, List<TicketComment> keyComments) {
        StringBuilder builder = new StringBuilder();
        builder.append(ticket.getTitle())
                .append("；分类=").append(enumCode(ticket.getCategory()))
                .append("；优先级=").append(enumCode(ticket.getPriority()));
        if (hasText(ticket.getSolutionSummary())) {
            builder.append("；解决=").append(ticket.getSolutionSummary());
        } else if (!keyComments.isEmpty()) {
            builder.append("；关键处理=").append(keyComments.get(0).getContent());
        }
        String summary = builder.toString();
        return summary.length() <= MAX_SUMMARY_LENGTH ? summary : summary.substring(0, MAX_SUMMARY_LENGTH);
    }

    /** 写入一行“字段：值”。 */
    private void appendLine(StringBuilder builder, String label, Object value) {
        builder.append(label).append("：").append(value == null ? "" : value).append("\n");
    }

    /** 评论优先级，数值越小越靠前。 */
    private int commentPriority(TicketComment comment) {
        if ("SOLUTION".equals(comment.getCommentType())) {
            return 0;
        }
        if ("PROCESS_LOG".equals(comment.getCommentType())) {
            return 1;
        }
        return 2;
    }

    /** 枚举编码安全转换。 */
    private String enumCode(CodeInfoEnum value) {
        return value == null ? "" : value.getCode();
    }

    /** 字符串非空判断。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 空字符串兜底。 */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
