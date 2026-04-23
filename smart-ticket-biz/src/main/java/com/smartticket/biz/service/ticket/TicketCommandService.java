package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.sla.TicketSlaService;
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

/**
 * 负责创建工单及创建阶段的前置校验。
 * 这里不处理后续流转，只关心入库前的默认值、幂等控制和类型约束。
 */
@Service
public class TicketCommandService {
    private final TicketServiceSupport support;
    private final TicketRepository ticketRepository;
    private final TicketIdempotencyService ticketIdempotencyService;
    private final TicketSlaService ticketSlaService;
    private final TicketTypeProfileService ticketTypeProfileService;

    public TicketCommandService(
            TicketServiceSupport support,
            TicketRepository ticketRepository,
            TicketIdempotencyService ticketIdempotencyService,
            TicketSlaService ticketSlaService,
            TicketTypeProfileService ticketTypeProfileService
    ) {
        this.support = support;
        this.ticketRepository = ticketRepository;
        this.ticketIdempotencyService = ticketIdempotencyService;
        this.ticketSlaService = ticketSlaService;
        this.ticketTypeProfileService = ticketTypeProfileService;
    }

    @Transactional
    public Ticket createTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        String idempotencyKey = normalizeIdempotencyKey(command);
        if (ticketIdempotencyService.enabled(idempotencyKey)) {
            return createTicketWithIdempotency(operator, command);
        }
        return doCreateTicket(operator, command);
    }

    private Ticket createTicketWithIdempotency(CurrentUser operator, TicketCreateCommandDTO command) {
        Long existingTicketId = findExistingTicketId(operator, command);
        if (existingTicketId != null) {
            return loadExistingCreatedTicket(existingTicketId);
        }
        String idempotencyKey = command.getIdempotencyKey();
        if (!ticketIdempotencyService.acquireCreateLock(operator.getUserId(), idempotencyKey)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENT_REQUEST_PROCESSING);
        }
        try {
            Ticket ticket = doCreateTicket(operator, command);
            support.saveIdempotencyResultAfterCommit(operator.getUserId(), idempotencyKey, ticket.getId());
            return ticket;
        } catch (RuntimeException ex) {
            ticketIdempotencyService.releaseCreateLock(operator.getUserId(), idempotencyKey);
            throw ex;
        }
    }

    private Ticket doCreateTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        TicketTypeEnum type = command.getType() == null ? TicketTypeEnum.INCIDENT : command.getType();
        validateByType(type, command);
        ticketTypeProfileService.validate(type, command.getTypeProfile());
        Ticket ticket = buildTicket(operator, command, type);
        ticketRepository.insert(ticket);
        ticketTypeProfileService.saveOrUpdate(ticket.getId(), type, command.getTypeProfile());
        support.writeLog(ticket.getId(), operator.getUserId(), OperationTypeEnum.CREATE, "创建工单", null, support.snapshot(ticket));
        Ticket created = support.requireTicket(ticket.getId());
        ticketTypeProfileService.attachProfile(created);
        ticketSlaService.createOrRefreshInstance(created);
        return created;
    }

    private String normalizeIdempotencyKey(TicketCreateCommandDTO command) {
        String idempotencyKey = ticketIdempotencyService.normalize(command.getIdempotencyKey());
        command.setIdempotencyKey(idempotencyKey);
        return idempotencyKey;
    }

    private Long findExistingTicketId(CurrentUser operator, TicketCreateCommandDTO command) {
        support.validateIdempotencyKey(command.getIdempotencyKey());
        return ticketIdempotencyService.getCreatedTicketId(operator.getUserId(), command.getIdempotencyKey());
    }

    private Ticket loadExistingCreatedTicket(Long ticketId) {
        Ticket ticket = support.requireTicket(ticketId);
        ticketTypeProfileService.attachProfile(ticket);
        return ticket;
    }

    private Ticket buildTicket(CurrentUser operator, TicketCreateCommandDTO command, TicketTypeEnum type) {
        return Ticket.builder()
                .ticketNo(support.generateTicketNo())
                .title(command.getTitle())
                .description(command.getDescription())
                .type(type)
                .category(resolveCategory(command, type))
                .priority(resolvePriority(command, type))
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(operator.getUserId())
                .source("MANUAL")
                .idempotencyKey(command.getIdempotencyKey())
                .build();
    }

    private void validateByType(TicketTypeEnum type, TicketCreateCommandDTO command) {
        String text = ((command.getTitle() == null ? "" : command.getTitle()) + " "
                + (command.getDescription() == null ? "" : command.getDescription())).trim();
        if (text.isEmpty()) {
            return;
        }
        switch (type) {
            case ACCESS_REQUEST -> requireAnyKeyword(text, "权限申请需要说明账号、角色或资源范围", "账号", "权限", "角色", "资源", "访问", "access", "role");
            case ENVIRONMENT_REQUEST -> requireAnyKeyword(text, "环境申请需要说明项目、环境或用途信息", "环境", "测试", "生产", "项目", "用途", "容器", "env");
            case CHANGE_REQUEST -> requireAnyKeyword(text, "变更申请需要说明发布内容、时间窗口或影响范围", "发布", "变更", "上线", "影响", "窗口", "change", "deploy");
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

    private TicketCategoryEnum resolveCategory(TicketCreateCommandDTO command, TicketTypeEnum type) {
        return command.getCategory() == null ? defaultCategory(type) : command.getCategory();
    }

    private TicketPriorityEnum resolvePriority(TicketCreateCommandDTO command, TicketTypeEnum type) {
        return command.getPriority() == null ? defaultPriority(type) : command.getPriority();
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
