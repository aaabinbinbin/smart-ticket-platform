package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.CodeInfoEnum;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TicketQueryService {
    private final TicketServiceSupport support;

    public TicketQueryService(TicketServiceSupport support) {
        this.support = support;
    }

    public TicketDetailDTO getDetail(CurrentUser operator, Long ticketId) {
        TicketDetailDTO cached = support.ticketDetailCacheService().get(ticketId);
        if (cached != null && cached.getTicket() != null) {
            support.requireVisibleFromTicket(operator, cached.getTicket());
            return cached;
        }
        Ticket ticket = support.requireVisibleTicket(operator, ticketId);
        TicketDetailDTO detail = support.buildDetail(ticketId, ticket);
        support.ticketDetailCacheService().put(ticketId, detail);
        return detail;
    }

    public PageResult<Ticket> pageTickets(CurrentUser operator, TicketPageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        int offset = (pageNo - 1) * pageSize;
        String status = enumCode(query.getStatus());
        String category = enumCode(query.getCategory());
        String priority = enumCode(query.getPriority());

        List<Ticket> records;
        long total;
        if (operator.isAdmin()) {
            records = support.ticketRepository().pageAll(status, category, priority, offset, pageSize);
            total = support.ticketRepository().countAll(status, category, priority);
        } else {
            records = support.ticketRepository().pageVisible(operator.getUserId(), status, category, priority, offset, pageSize);
            total = support.ticketRepository().countVisible(operator.getUserId(), status, category, priority);
        }

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
