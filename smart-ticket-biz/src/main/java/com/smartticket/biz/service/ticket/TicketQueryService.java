package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketDetailDTO;
import com.smartticket.biz.dto.ticket.TicketPageQueryDTO;
import com.smartticket.biz.dto.ticket.TicketSummaryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.approval.TicketApprovalService;
import com.smartticket.biz.service.type.TicketTypeProfileService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.CodeInfoEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TicketQueryService {
    private final TicketServiceSupport support;
    private final TicketTypeProfileService ticketTypeProfileService;
    private final TicketApprovalService ticketApprovalService;
    private final TicketSummaryService ticketSummaryService;

    public TicketQueryService(
            TicketServiceSupport support,
            TicketTypeProfileService ticketTypeProfileService,
            TicketApprovalService ticketApprovalService,
            TicketSummaryService ticketSummaryService
    ) {
        this.support = support;
        this.ticketTypeProfileService = ticketTypeProfileService;
        this.ticketApprovalService = ticketApprovalService;
        this.ticketSummaryService = ticketSummaryService;
    }

    public TicketDetailDTO getDetail(CurrentUser operator, Long ticketId) {
        TicketDetailDTO cached = support.ticketDetailCacheService().get(ticketId);
        if (cached != null && cached.getTicket() != null) {
            support.requireVisibleFromTicket(operator, cached.getTicket());
            if (cached.getSummaries() == null) {
                cached.setSummaries(ticketSummaryService.generateAll(cached));
            }
            return cached;
        }
        Ticket ticket = support.requireVisibleTicket(operator, ticketId);
        ticketTypeProfileService.attachProfile(ticket);
        TicketDetailDTO detail = support.buildDetail(ticketId, ticket);
        detail.setApproval(ticketApprovalService.getApproval(operator, ticketId));
        detail.setSummaries(ticketSummaryService.generateAll(detail));
        support.ticketDetailCacheService().put(ticketId, detail);
        return detail;
    }

    public TicketSummaryDTO getSummary(CurrentUser operator, Long ticketId, TicketSummaryViewEnum requestedView) {
        TicketDetailDTO detail = getDetail(operator, ticketId);
        TicketSummaryViewEnum view = ticketSummaryService.resolveView(operator, detail.getTicket(), requestedView);
        return ticketSummaryService.generateForView(detail, view);
    }

    public PageResult<Ticket> pageTickets(CurrentUser operator, TicketPageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        int offset = (pageNo - 1) * pageSize;
        String status = enumCode(query.getStatus());
        String type = enumCode(query.getType());
        String category = enumCode(query.getCategory());
        String priority = enumCode(query.getPriority());

        List<Ticket> records;
        long total;
        if (operator.isAdmin()) {
            records = support.ticketRepository().pageAll(status, type, category, priority, offset, pageSize);
            total = support.ticketRepository().countAll(status, type, category, priority);
        } else {
            records = support.ticketRepository().pageVisible(operator.getUserId(), status, type, category, priority, offset, pageSize);
            total = support.ticketRepository().countVisible(operator.getUserId(), status, type, category, priority);
        }
        ticketTypeProfileService.attachProfiles(records);

        return PageResult.<Ticket>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    private String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }
}

