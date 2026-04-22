package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.type.TicketTypeProfileService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketCommandService {
    private final TicketServiceSupport support;
    private final TicketTypeProfileService ticketTypeProfileService;

    public TicketCommandService(TicketServiceSupport support, TicketTypeProfileService ticketTypeProfileService) {
        this.support = support;
        this.ticketTypeProfileService = ticketTypeProfileService;
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
            Ticket ticket = support.requireTicket(existingTicketId);
            ticketTypeProfileService.attachProfile(ticket);
            return ticket;
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
        TicketTypeEnum type = command.getType() == null ? TicketTypeEnum.INCIDENT : command.getType();
        validateByType(type, command);
        ticketTypeProfileService.validate(type, command.getTypeProfile());
        TicketCategoryEnum category = command.getCategory() == null ? defaultCategory(type) : command.getCategory();
        TicketPriorityEnum priority = command.getPriority() == null ? defaultPriority(type) : command.getPriority();

        Ticket ticket = Ticket.builder()
                .ticketNo(support.generateTicketNo())
                .title(command.getTitle())
                .description(command.getDescription())
                .type(type)
                .category(category)
                .priority(priority)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(operator.getUserId())
                .source("MANUAL")
                .idempotencyKey(command.getIdempotencyKey())
                .build();
        support.ticketRepository().insert(ticket);
        ticketTypeProfileService.saveOrUpdate(ticket.getId(), type, command.getTypeProfile());
        support.writeLog(ticket.getId(), operator.getUserId(), OperationTypeEnum.CREATE, "��������", null, support.snapshot(ticket));
        Ticket created = support.requireTicket(ticket.getId());
        ticketTypeProfileService.attachProfile(created);
        support.ticketSlaService().createOrRefreshInstance(created);
        return created;
    }

    private void validateByType(TicketTypeEnum type, TicketCreateCommandDTO command) {
        String text = ((command.getTitle() == null ? "" : command.getTitle()) + " "
                + (command.getDescription() == null ? "" : command.getDescription())).trim();
        if (text.isEmpty()) {
            return;
        }
        switch (type) {
            case ACCESS_REQUEST -> requireAnyKeyword(text, "Ȩ��������Ҫ˵���˺š���ɫ����Դ��Χ", "�˺�", "Ȩ��", "��ɫ", "��Դ", "����", "access", "role");
            case ENVIRONMENT_REQUEST -> requireAnyKeyword(text, "����������Ҫ˵��Ŀ�껷���������������", "����", "����", "����", "����", "����", "���ݿ�", "env");
            case CHANGE_REQUEST -> requireAnyKeyword(text, "���������Ҫ˵���������ʱ�䴰�ڻ�Ӱ�췶Χ", "���", "����", "����", "Ӱ��", "�ع�", "change", "deploy");
            default -> {
            }
        }
    }

    private void requireAnyKeyword(String text, String message, String... keywords) {
        String normalized = text.toLowerCase();
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase())) {
                return;
            }
        }
        throw new BusinessException(BusinessErrorCode.INVALID_TICKET_TYPE_REQUIREMENT, message);
    }

    private TicketCategoryEnum defaultCategory(TicketTypeEnum type) {
        return switch (type) {
            case ACCESS_REQUEST -> TicketCategoryEnum.ACCOUNT;
            case ENVIRONMENT_REQUEST -> TicketCategoryEnum.ENVIRONMENT;
            case CONSULTATION -> TicketCategoryEnum.OTHER;
            case CHANGE_REQUEST, INCIDENT -> TicketCategoryEnum.SYSTEM;
        };
    }

    private TicketPriorityEnum defaultPriority(TicketTypeEnum type) {
        return switch (type) {
            case CHANGE_REQUEST -> TicketPriorityEnum.HIGH;
            case INCIDENT, ACCESS_REQUEST, ENVIRONMENT_REQUEST, CONSULTATION -> TicketPriorityEnum.MEDIUM;
        };
    }
}

