package com.smartticket.biz.service;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketQueueBindingService {
    private final TicketServiceSupport support;

    public TicketQueueBindingService(TicketServiceSupport support) {
        this.support = support;
    }

    @Transactional
    public Ticket bindTicketQueue(CurrentUser operator, Long ticketId, Long groupId, Long queueId) {
        support.permissionService().requireAdmin(operator);
        Ticket before = support.requireTicket(ticketId);
        if (before.getStatus() == TicketStatusEnum.CLOSED) {
            throw new BusinessException(BusinessErrorCode.TICKET_CLOSED);
        }
        support.validateQueueBinding(groupId, queueId);

        support.requireUpdated(support.ticketRepository().updateQueueBinding(ticketId, groupId, queueId));
        Ticket after = support.requireTicket(ticketId);
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.BIND_QUEUE, "绑定工单队列", support.snapshot(before), support.snapshot(after));
        support.ticketDetailCacheService().evict(ticketId);
        return after;
    }
}
