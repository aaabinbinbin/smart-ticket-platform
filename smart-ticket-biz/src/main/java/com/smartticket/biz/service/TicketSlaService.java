package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketSlaPolicyCommandDTO;
import com.smartticket.biz.dto.TicketSlaPolicyPageQueryDTO;
import com.smartticket.biz.dto.TicketSlaScanResultDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketOperationLogRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.biz.repository.TicketSlaInstanceRepository;
import com.smartticket.biz.repository.TicketSlaPolicyRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.entity.TicketSlaInstance;
import com.smartticket.domain.entity.TicketSlaPolicy;
import com.smartticket.domain.enums.CodeInfoEnum;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketSlaService {
    private static final int DEFAULT_SCAN_LIMIT = 100;
    private static final int MAX_SCAN_LIMIT = 1000;

    private final TicketSlaPolicyRepository policyRepository;
    private final TicketSlaInstanceRepository instanceRepository;
    private final TicketRepository ticketRepository;
    private final TicketOperationLogRepository operationLogRepository;
    private final TicketPermissionService permissionService;
    private final TicketDetailCacheService ticketDetailCacheService;
    private final TicketSlaNotificationService notificationService;

    public TicketSlaService(
            TicketSlaPolicyRepository policyRepository,
            TicketSlaInstanceRepository instanceRepository,
            TicketRepository ticketRepository,
            TicketOperationLogRepository operationLogRepository,
            TicketPermissionService permissionService,
            TicketDetailCacheService ticketDetailCacheService,
            TicketSlaNotificationService notificationService
    ) {
        this.policyRepository = policyRepository;
        this.instanceRepository = instanceRepository;
        this.ticketRepository = ticketRepository;
        this.operationLogRepository = operationLogRepository;
        this.permissionService = permissionService;
        this.ticketDetailCacheService = ticketDetailCacheService;
        this.notificationService = notificationService;
    }

    @Transactional
    public TicketSlaPolicy createPolicy(CurrentUser operator, TicketSlaPolicyCommandDTO command) {
        permissionService.requireAdmin(operator);
        validatePolicy(command);
        TicketSlaPolicy policy = TicketSlaPolicy.builder()
                .policyName(command.getPolicyName())
                .category(enumCode(command.getCategory()))
                .priority(enumCode(command.getPriority()))
                .firstResponseMinutes(command.getFirstResponseMinutes())
                .resolveMinutes(command.getResolveMinutes())
                .enabled(toEnabled(command.getEnabled()))
                .build();
        policyRepository.insert(policy);
        return requirePolicy(policy.getId());
    }

    @Transactional
    public TicketSlaPolicy updatePolicy(CurrentUser operator, Long policyId, TicketSlaPolicyCommandDTO command) {
        permissionService.requireAdmin(operator);
        validatePolicy(command);
        TicketSlaPolicy policy = requirePolicy(policyId);
        policy.setPolicyName(command.getPolicyName());
        policy.setCategory(enumCode(command.getCategory()));
        policy.setPriority(enumCode(command.getPriority()));
        policy.setFirstResponseMinutes(command.getFirstResponseMinutes());
        policy.setResolveMinutes(command.getResolveMinutes());
        policy.setEnabled(toEnabled(command.getEnabled()));
        policyRepository.update(policy);
        return requirePolicy(policyId);
    }

    @Transactional
    public TicketSlaPolicy updatePolicyEnabled(CurrentUser operator, Long policyId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requirePolicy(policyId);
        policyRepository.updateEnabled(policyId, enabled ? 1 : 0);
        return requirePolicy(policyId);
    }

    public TicketSlaPolicy getPolicy(Long policyId) {
        return requirePolicy(policyId);
    }

    public PageResult<TicketSlaPolicy> pagePolicies(TicketSlaPolicyPageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        int offset = (pageNo - 1) * pageSize;
        String category = enumCode(query.getCategory());
        String priority = enumCode(query.getPriority());
        Integer enabled = query.getEnabled() == null ? null : toEnabled(query.getEnabled());
        List<TicketSlaPolicy> records = policyRepository.page(category, priority, enabled, offset, pageSize);
        long total = policyRepository.count(category, priority, enabled);
        return PageResult.<TicketSlaPolicy>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    @Transactional
    public void createOrRefreshInstance(Ticket ticket) {
        if (ticket == null || ticket.getId() == null) {
            return;
        }
        TicketSlaPolicy policy = policyRepository.findBestMatch(enumCode(ticket.getCategory()), enumCode(ticket.getPriority()));
        if (policy == null) {
            return;
        }
        LocalDateTime baseTime = ticket.getCreatedAt() == null ? LocalDateTime.now() : ticket.getCreatedAt();
        TicketSlaInstance instance = TicketSlaInstance.builder()
                .ticketId(ticket.getId())
                .policyId(policy.getId())
                .firstResponseDeadline(baseTime.plusMinutes(policy.getFirstResponseMinutes()))
                .resolveDeadline(baseTime.plusMinutes(policy.getResolveMinutes()))
                .breached(0)
                .build();
        if (instanceRepository.findByTicketId(ticket.getId()) == null) {
            instanceRepository.insert(instance);
        } else {
            instanceRepository.updateByTicketId(instance);
        }
    }

    public TicketSlaInstance getInstanceByTicketId(Long ticketId) {
        TicketSlaInstance instance = instanceRepository.findByTicketId(ticketId);
        if (instance == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_SLA_INSTANCE_NOT_FOUND);
        }
        return instance;
    }

    @Transactional
    public TicketSlaScanResultDTO scanBreachedInstances(CurrentUser operator, Integer limit) {
        return scanBreachedInstances(operator, LocalDateTime.now(), limit);
    }

    @Transactional
    public TicketSlaScanResultDTO scanBreachedInstances(CurrentUser operator, LocalDateTime now, Integer limit) {
        permissionService.requireAdmin(operator);
        return doScanBreachedInstances(now, limit);
    }

    @Transactional
    public TicketSlaScanResultDTO scanBreachedInstancesAutomatically() {
        return doScanBreachedInstances(LocalDateTime.now(), null);
    }

    private TicketSlaScanResultDTO doScanBreachedInstances(LocalDateTime now, Integer limit) {
        int normalizedLimit = normalizeScanLimit(limit);
        LocalDateTime scanTime = now == null ? LocalDateTime.now() : now;
        List<TicketSlaInstance> candidates = instanceRepository.findBreachedCandidates(scanTime, normalizedLimit);
        List<Long> markedIds = new ArrayList<>();
        int firstResponseBreachedCount = 0;
        int resolveBreachedCount = 0;
        int escalatedCount = 0;
        int notifiedCount = 0;
        Optional<Long> adminUserId = findEscalationAdminUserId();
        for (TicketSlaInstance candidate : candidates) {
            if (candidate.getId() == null || candidate.getTicketId() == null) {
                continue;
            }
            Ticket ticket = ticketRepository.findById(candidate.getTicketId());
            if (ticket == null) {
                continue;
            }
            String breachType = determineBreachType(ticket, candidate, scanTime);
            if (breachType == null) {
                continue;
            }
            if (instanceRepository.markBreached(candidate.getId()) <= 0) {
                continue;
            }
            markedIds.add(candidate.getId());
            if ("FIRST_RESPONSE".equals(breachType)) {
                firstResponseBreachedCount++;
            } else {
                resolveBreachedCount++;
            }
            boolean escalated = escalateTicket(ticket, adminUserId.orElse(null), breachType);
            if (escalated) {
                escalatedCount++;
            }
            Ticket latestTicket = ticketRepository.findById(ticket.getId());
            notificationService.notifyBreached(latestTicket == null ? ticket : latestTicket, candidate, breachType, escalated);
            notifiedCount++;
            writeAuditLog(ticket.getId(), resolveOperatorId(ticket, adminUserId.orElse(null)), OperationTypeEnum.SLA_BREACH, "SLA违约", "instanceId=" + candidate.getId(), "breachType=" + breachType + ", escalated=" + escalated);
            if (escalated) {
                writeAuditLog(ticket.getId(), resolveOperatorId(ticket, adminUserId.orElse(null)), OperationTypeEnum.SLA_ESCALATE, "SLA升级", null, "breachType=" + breachType + ", adminUserId=" + adminUserId.orElse(null));
            }
        }
        return TicketSlaScanResultDTO.builder()
                .scanTime(scanTime)
                .limit(normalizedLimit)
                .candidateCount(candidates.size())
                .markedCount(markedIds.size())
                .firstResponseBreachedCount(firstResponseBreachedCount)
                .resolveBreachedCount(resolveBreachedCount)
                .escalatedCount(escalatedCount)
                .notifiedCount(notifiedCount)
                .breachedInstanceIds(markedIds)
                .build();
    }

    private Optional<Long> findEscalationAdminUserId() {
        return ticketRepository.findUsersByRoleCode("ADMIN").stream()
                .filter(user -> Integer.valueOf(1).equals(user.getStatus()))
                .map(user -> user.getId())
                .findFirst();
    }

    private String determineBreachType(Ticket ticket, TicketSlaInstance instance, LocalDateTime now) {
        if (instance.getFirstResponseDeadline() != null && !now.isBefore(instance.getFirstResponseDeadline()) && ticket.getStatus() == TicketStatusEnum.PENDING_ASSIGN && ticket.getAssigneeId() == null) {
            return "FIRST_RESPONSE";
        }
        if (instance.getResolveDeadline() != null && !now.isBefore(instance.getResolveDeadline()) && ticket.getStatus() != TicketStatusEnum.CLOSED) {
            return "RESOLVE";
        }
        return null;
    }

    private boolean escalateTicket(Ticket ticket, Long adminUserId, String breachType) {
        boolean changed = false;
        if (ticket.getPriority() != TicketPriorityEnum.URGENT) {
            changed = ticketRepository.updatePriority(ticket.getId(), TicketPriorityEnum.URGENT) > 0;
        }
        if ("FIRST_RESPONSE".equals(breachType) && adminUserId != null && ticket.getStatus() == TicketStatusEnum.PENDING_ASSIGN && ticket.getAssigneeId() == null) {
            changed = ticketRepository.updateAssigneeAndStatus(ticket.getId(), adminUserId, TicketStatusEnum.PENDING_ASSIGN, TicketStatusEnum.PROCESSING) > 0 || changed;
        }
        if (changed) {
            ticketDetailCacheService.evict(ticket.getId());
        }
        return changed;
    }

    private Long resolveOperatorId(Ticket ticket, Long adminUserId) {
        if (adminUserId != null) {
            return adminUserId;
        }
        if (ticket.getAssigneeId() != null) {
            return ticket.getAssigneeId();
        }
        return ticket.getCreatorId();
    }

    private void writeAuditLog(Long ticketId, Long operatorId, OperationTypeEnum operationType, String operationDesc, String beforeValue, String afterValue) {
        operationLogRepository.insert(TicketOperationLog.builder()
                .ticketId(ticketId)
                .operatorId(operatorId)
                .operationType(operationType)
                .operationDesc(operationDesc)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    private TicketSlaPolicy requirePolicy(Long policyId) {
        TicketSlaPolicy policy = policyRepository.findById(policyId);
        if (policy == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_SLA_POLICY_NOT_FOUND);
        }
        return policy;
    }

    private void validatePolicy(TicketSlaPolicyCommandDTO command) {
        if (command.getFirstResponseMinutes() == null || command.getFirstResponseMinutes() <= 0) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_SLA_POLICY, "首次响应时限必须大于 0");
        }
        if (command.getResolveMinutes() == null || command.getResolveMinutes() <= 0) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_SLA_POLICY, "解决时限必须大于 0");
        }
        if (command.getResolveMinutes() < command.getFirstResponseMinutes()) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_SLA_POLICY, "解决时限不能小于首次响应时限");
        }
    }

    private String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }

    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }

    private int normalizeScanLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_SCAN_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_SCAN_LIMIT);
    }
}