package com.smartticket.api.assembler;

import com.smartticket.api.vo.ticket.TicketCommentVO;
import com.smartticket.api.vo.ticket.TicketDetailVO;
import com.smartticket.api.vo.ticket.TicketOperationLogVO;
import com.smartticket.api.vo.ticket.TicketVO;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import org.springframework.stereotype.Component;

/**
 * 工单 API 响应组装器。
 *
 * <p>负责把 domain/biz 对象转换成 HTTP VO，避免 Controller 中堆积字段转换代码。</p>
 */
@Component
public class TicketAssembler {

    public TicketVO toVO(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return TicketVO.builder()
                .id(ticket.getId())
                .ticketNo(ticket.getTicketNo())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .category(ticket.getCategory() == null ? null : ticket.getCategory().getCode())
                .categoryInfo(ticket.getCategory() == null ? null : ticket.getCategory().getInfo())
                .priority(ticket.getPriority() == null ? null : ticket.getPriority().getCode())
                .priorityInfo(ticket.getPriority() == null ? null : ticket.getPriority().getInfo())
                .status(ticket.getStatus() == null ? null : ticket.getStatus().getCode())
                .statusInfo(ticket.getStatus() == null ? null : ticket.getStatus().getInfo())
                .creatorId(ticket.getCreatorId())
                .assigneeId(ticket.getAssigneeId())
                .groupId(ticket.getGroupId())
                .queueId(ticket.getQueueId())
                .solutionSummary(ticket.getSolutionSummary())
                .source(ticket.getSource())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    public TicketCommentVO toCommentVO(TicketComment comment) {
        return TicketCommentVO.builder()
                .id(comment.getId())
                .ticketId(comment.getTicketId())
                .commenterId(comment.getCommenterId())
                .commentType(comment.getCommentType())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    public TicketOperationLogVO toLogVO(TicketOperationLog log) {
        return TicketOperationLogVO.builder()
                .id(log.getId())
                .ticketId(log.getTicketId())
                .operatorId(log.getOperatorId())
                .operationType(log.getOperationType() == null ? null : log.getOperationType().getCode())
                .operationTypeInfo(log.getOperationType() == null ? null : log.getOperationType().getInfo())
                .operationDesc(log.getOperationDesc())
                .beforeValue(log.getBeforeValue())
                .afterValue(log.getAfterValue())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public TicketDetailVO toDetailVO(TicketDetailDTO detail) {
        return TicketDetailVO.builder()
                .ticket(toVO(detail.getTicket()))
                .comments(detail.getComments().stream().map(this::toCommentVO).toList())
                .operationLogs(detail.getOperationLogs().stream().map(this::toLogVO).toList())
                .build();
    }
}
