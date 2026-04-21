package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketCommandService {
    private final TicketServiceSupport support;

    public TicketCommandService(TicketServiceSupport support) {
        this.support = support;
    }

    @Transactional
    public Ticket createTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        if (support.ticketIdempotencyService().enabled(command.getIdempotencyKey())) {
            return createTicketWithIdempotency(operator, command);
        }
        return doCreateTicket(operator, command);
    }

    private Ticket createTicketWithIdempotency(CurrentUser operator, TicketCreateCommandDTO command) {
        String idempotencyKey = support.ticketIdempotencyService().normalize(command.getIdempotencyKey());
        support.validateIdempotencyKey(idempotencyKey);
        command.setIdempotencyKey(idempotencyKey);
        Long existingTicketId = support.ticketIdempotencyService().getCreatedTicketId(operator.getUserId(), idempotencyKey);
        if (existingTicketId != null) {
            return support.requireTicket(existingTicketId);
        }
        if (!support.ticketIdempotencyService().acquireCreateLock(operator.getUserId(), idempotencyKey)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENT_REQUEST_PROCESSING);
        }
        try {
            Ticket ticket = doCreateTicket(operator, command);
            support.saveIdempotencyResultAfterCommit(operator.getUserId(), idempotencyKey, ticket.getId());
            return ticket;
        } catch (RuntimeException ex) {
            support.ticketIdempotencyService().releaseCreateLock(operator.getUserId(), idempotencyKey);
            throw ex;
        }
    }

    private Ticket doCreateTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        Ticket ticket = Ticket.builder()
                .ticketNo(support.generateTicketNo())
                .title(command.getTitle())
                .description(command.getDescription())
                .category(command.getCategory())
                .priority(command.getPriority())
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(operator.getUserId())
                .source("MANUAL")
                .idempotencyKey(command.getIdempotencyKey())
                .build();
        support.ticketRepository().insert(ticket);
        support.writeLog(ticket.getId(), operator.getUserId(), OperationTypeEnum.CREATE, "创建工单", null, support.snapshot(ticket));
        Ticket created = support.requireTicket(ticket.getId());
        support.ticketSlaService().createOrRefreshInstance(created);
        return created;
    }
}
