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
 * 工单知识生产服务。
 *
 * <p>该服务属于 biz 模块，负责把已经关闭且具备沉淀价值的工单转成标准化知识文本，
 * 并写入 {@code ticket_knowledge}。它不调用 Spring AI，不做向量化，不做向量检索，
 * 也不承担工单关闭主流程。</p>
 */
@Service
public class TicketKnowledgeService {
    /**
     * 单个知识文本最多保留的关键评论数量。
     */
    private static final int MAX_KEY_COMMENTS = 8;

    /**
     * 摘要最大长度，需小于 ticket_knowledge.content_summary 字段上限。
     */
    private static final int MAX_SUMMARY_LENGTH = 900;

    /**
     * 知识文本中的时间格式。
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 工单仓储，用于读取当前工单事实。
     */
    private final TicketRepository ticketRepository;

    /**
     * 评论仓储，用于读取工单协作过程。
     */
    private final TicketCommentRepository ticketCommentRepository;

    /**
     * 知识仓储，用于写入或更新 ticket_knowledge。
     */
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
     * <p>第一版只沉淀已关闭工单，并要求至少存在解决摘要或关键评论。这样可以避免
     * 把未完成、未验证或缺少处理信息的问题写入知识库。</p>
     *
     * @param ticket 工单事实对象
     * @param comments 工单评论列表
     * @return 满足沉淀条件时返回 true
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
     * <p>该方法只负责知识生产和 {@code ticket_knowledge} 写入。关闭工单的状态流转、
     * 权限校验、日志和事务主流程由 {@link TicketService} 负责。</p>
     *
     * @param ticketId 来源工单 ID
     * @return 成功构建的知识记录；不满足沉淀条件时返回空
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
     * <p>优先级：SOLUTION 评论 > PROCESS_LOG 评论 > 其他有内容的评论。第一版不调用
     * LLM 做摘要抽取，保证知识生产链路可解释、可回放。</p>
     *
     * @param comments 工单评论列表
     * @return 关键评论列表
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

    /**
     * 生成标准化知识正文。
     *
     * <p>该文本是后续 RAG 切片和向量化的源材料，但本服务不执行切片、embedding 或检索。</p>
     */
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

    /**
     * 生成知识摘要，供列表展示和检索结果展示使用。
     */
    private String buildSummary(Ticket ticket, List<TicketComment> keyComments) {
        StringBuilder builder = new StringBuilder();
        builder.append(nullToEmpty(ticket.getTitle()))
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

    /**
     * 追加一行标准化字段。
     */
    private void appendLine(StringBuilder builder, String label, Object value) {
        builder.append(label).append("：").append(value == null ? "" : value).append("\n");
    }

    /**
     * 评论优先级，数值越小越靠前。
     */
    private int commentPriority(TicketComment comment) {
        if ("SOLUTION".equals(comment.getCommentType())) {
            return 0;
        }
        if ("PROCESS_LOG".equals(comment.getCommentType())) {
            return 1;
        }
        return 2;
    }

    /**
     * 枚举编码安全转换。
     */
    private String enumCode(CodeInfoEnum value) {
        return value == null ? "" : value.getCode();
    }

    /**
     * 判断字符串是否有有效内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 空字符串兜底。
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
