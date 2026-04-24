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
    // 支撑
    private final TicketServiceSupport support;
    // 工单仓储
    private final TicketRepository ticketRepository;
    // 工单幂等服务
    private final TicketIdempotencyService ticketIdempotencyService;
    // 工单SLA服务
    private final TicketSlaService ticketSlaService;
    // 工单类型画像服务
    private final TicketTypeProfileService ticketTypeProfileService;

    /**
     * 构造工单命令服务。
     */
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

    /**
     * 创建工单。
     */
    @Transactional
    public Ticket createTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        // 先统一规范化幂等键，保证后续所有分支使用同一份值。
        String idempotencyKey = normalizeIdempotencyKey(command);
        if (ticketIdempotencyService.enabled(idempotencyKey)) {
            // 开启幂等控制时，优先走带锁的创建流程，避免重复提交写入多条工单。
            return createTicketWithIdempotency(operator, command);
        }
        return doCreateTicket(operator, command);
    }

    /**
     * 创建工单并处理幂等。
     */
    private Ticket createTicketWithIdempotency(CurrentUser operator, TicketCreateCommandDTO command) {
        Long existingTicketId = findExistingTicketId(operator, command);
        if (existingTicketId != null) {
            // 已经有成功创建的结果时直接复用，避免重复执行后续写库逻辑。
            return loadExistingCreatedTicket(existingTicketId);
        }
        String idempotencyKey = command.getIdempotencyKey();
        if (!ticketIdempotencyService.acquireCreateLock(operator.getUserId(), idempotencyKey)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENT_REQUEST_PROCESSING);
        }
        try {
            Ticket ticket = doCreateTicket(operator, command);
            // 只在事务提交后记录幂等结果，防止主事务回滚后留下脏的成功记录。
            support.saveIdempotencyResultAfterCommit(operator.getUserId(), idempotencyKey, ticket.getId());
            return ticket;
        } catch (RuntimeException ex) {
            // 创建失败时立即释放锁，避免同一个幂等键长时间无法重试。
            ticketIdempotencyService.releaseCreateLock(operator.getUserId(), idempotencyKey);
            throw ex;
        }
    }

    /**
     * 执行创建工单。
     */
    private Ticket doCreateTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        TicketTypeEnum type = command.getType() == null ? TicketTypeEnum.INCIDENT : command.getType();
        // 按工单类型做额外语义校验，避免关键字段缺失但仍被创建。
        validateByType(type, command);
        ticketTypeProfileService.validate(type, command.getTypeProfile());
        Ticket ticket = buildTicket(operator, command, type);
        ticketRepository.insert(ticket);
        ticketTypeProfileService.saveOrUpdate(ticket.getId(), type, command.getTypeProfile());
        support.writeLog(ticket.getId(), operator.getUserId(), OperationTypeEnum.CREATE, "创建工单", null, support.snapshot(ticket));
        Ticket created = support.requireTicket(ticket.getId());
        // 创建完成后补齐类型画像并初始化 SLA，保证返回结果是可直接展示的完整对象。
        ticketTypeProfileService.attachProfile(created);
        ticketSlaService.createOrRefreshInstance(created);
        return created;
    }

    /**
     * 规范化幂等键。
     */
    private String normalizeIdempotencyKey(TicketCreateCommandDTO command) {
        String idempotencyKey = ticketIdempotencyService.normalize(command.getIdempotencyKey());
        command.setIdempotencyKey(idempotencyKey);
        return idempotencyKey;
    }

    /**
     * 查询已存在工单ID。
     */
    private Long findExistingTicketId(CurrentUser operator, TicketCreateCommandDTO command) {
        support.validateIdempotencyKey(command.getIdempotencyKey());
        return ticketIdempotencyService.getCreatedTicketId(operator.getUserId(), command.getIdempotencyKey());
    }

    /**
     * 加载已存在已创建工单。
     */
    private Ticket loadExistingCreatedTicket(Long ticketId) {
        Ticket ticket = support.requireTicket(ticketId);
        ticketTypeProfileService.attachProfile(ticket);
        return ticket;
    }

    /**
     * 构建工单。
     */
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

    /**
     * 校验按类型。
     */
    private void validateByType(TicketTypeEnum type, TicketCreateCommandDTO command) {
        String text = ((command.getTitle() == null ? "" : command.getTitle()) + " "
                + (command.getDescription() == null ? "" : command.getDescription())).trim();
        if (text.isEmpty()) {
            return;
        }
        // 不同类型工单要求在标题或描述中体现关键信息，用于降低无效单比例。
        switch (type) {
            case ACCESS_REQUEST -> requireAnyKeyword(text, "权限申请需要说明账号、角色或资源范围", "账号", "权限", "角色", "资源", "访问", "access", "role");
            case ENVIRONMENT_REQUEST -> requireAnyKeyword(text, "环境申请需要说明项目、环境或用途信息", "环境", "测试", "生产", "项目", "用途", "容器", "env");
            case CHANGE_REQUEST -> requireAnyKeyword(text, "变更申请需要说明发布内容、时间窗口或影响范围", "发布", "变更", "上线", "影响", "窗口", "change", "deploy");
            default -> {
            }
        }
    }

    /**
     * 校验任意关键字。
     */
    private void requireAnyKeyword(String text, String message, String... keywords) {
        String normalized = text.toLowerCase();
        for (String keyword : keywords) {
            // 只要命中任意一个关键字，就认为描述满足当前类型的最小信息要求。
            if (normalized.contains(keyword.toLowerCase())) {
                return;
            }
        }
        throw new BusinessException(BusinessErrorCode.INVALID_TICKET_TYPE_REQUIREMENT, message);
    }

    /**
     * 解析分类。
     */
    private TicketCategoryEnum resolveCategory(TicketCreateCommandDTO command, TicketTypeEnum type) {
        return command.getCategory() == null ? defaultCategory(type) : command.getCategory();
    }

    /**
     * 解析优先级。
     */
    private TicketPriorityEnum resolvePriority(TicketCreateCommandDTO command, TicketTypeEnum type) {
        return command.getPriority() == null ? defaultPriority(type) : command.getPriority();
    }

    /**
     * 获取默认分类。
     */
    private TicketCategoryEnum defaultCategory(TicketTypeEnum type) {
        return switch (type) {
            case ACCESS_REQUEST -> TicketCategoryEnum.ACCOUNT;
            case ENVIRONMENT_REQUEST -> TicketCategoryEnum.ENVIRONMENT;
            case CONSULTATION -> TicketCategoryEnum.OTHER;
            case CHANGE_REQUEST, INCIDENT -> TicketCategoryEnum.SYSTEM;
        };
    }

    /**
     * 获取默认优先级。
     */
    private TicketPriorityEnum defaultPriority(TicketTypeEnum type) {
        return switch (type) {
            case CHANGE_REQUEST -> TicketPriorityEnum.HIGH;
            case INCIDENT, ACCESS_REQUEST, ENVIRONMENT_REQUEST, CONSULTATION -> TicketPriorityEnum.MEDIUM;
        };
    }
}
