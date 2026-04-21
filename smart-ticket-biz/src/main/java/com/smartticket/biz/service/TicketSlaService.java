package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketSlaPolicyCommandDTO;
import com.smartticket.biz.dto.TicketSlaPolicyPageQueryDTO;
import com.smartticket.biz.dto.TicketSlaScanResultDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketSlaInstanceRepository;
import com.smartticket.biz.repository.TicketSlaPolicyRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketSlaInstance;
import com.smartticket.domain.entity.TicketSlaPolicy;
import com.smartticket.domain.enums.CodeInfoEnum;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单 SLA 业务服务。
 *
 * <p>当前 P1 第一版只负责 SLA 策略配置、工单 SLA 实例生成和查询。
 * 不做定时违约扫描、升级策略和自动通知。</p>
 */
@Service
public class TicketSlaService {
    /** SLA 违约扫描默认批次大小。 */
    private static final int DEFAULT_SCAN_LIMIT = 100;

    /** SLA 违约扫描单次最大批次大小，避免一次手动操作拖垮数据库。 */
    private static final int MAX_SCAN_LIMIT = 1000;

    /** SLA 策略仓储。 */
    private final TicketSlaPolicyRepository policyRepository;

    /** SLA 实例仓储。 */
    private final TicketSlaInstanceRepository instanceRepository;

    /** 权限服务，用于复用 ADMIN 判断。 */
    private final TicketPermissionService permissionService;

    public TicketSlaService(
            TicketSlaPolicyRepository policyRepository,
            TicketSlaInstanceRepository instanceRepository,
            TicketPermissionService permissionService
    ) {
        this.policyRepository = policyRepository;
        this.instanceRepository = instanceRepository;
        this.permissionService = permissionService;
    }

    /** 创建 SLA 策略。 */
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

    /** 更新 SLA 策略。 */
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

    /** 启用或停用 SLA 策略。 */
    @Transactional
    public TicketSlaPolicy updatePolicyEnabled(CurrentUser operator, Long policyId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requirePolicy(policyId);
        policyRepository.updateEnabled(policyId, enabled ? 1 : 0);
        return requirePolicy(policyId);
    }

    /** 查询 SLA 策略详情。 */
    public TicketSlaPolicy getPolicy(Long policyId) {
        return requirePolicy(policyId);
    }

    /** 分页查询 SLA 策略。 */
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

    /**
     * 为工单生成或刷新 SLA 实例。
     *
     * <p>如果没有匹配策略，则不创建实例。该方法不阻断工单主流程。</p>
     */
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

    /** 查询某张工单的 SLA 实例。 */
    public TicketSlaInstance getInstanceByTicketId(Long ticketId) {
        TicketSlaInstance instance = instanceRepository.findByTicketId(ticketId);
        if (instance == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_SLA_INSTANCE_NOT_FOUND);
        }
        return instance;
    }

    /** 根据 ID 查询策略，不存在时抛出业务异常。 */
    /** 扫描并标记已经超过解决截止时间的 SLA 实例。 */
    @Transactional
    public TicketSlaScanResultDTO scanBreachedInstances(CurrentUser operator, Integer limit) {
        return scanBreachedInstances(operator, LocalDateTime.now(), limit);
    }

    /** 按指定业务时间扫描 SLA 违约实例，主要用于测试和后续调度复用。 */
    @Transactional
    public TicketSlaScanResultDTO scanBreachedInstances(CurrentUser operator, LocalDateTime now, Integer limit) {
        permissionService.requireAdmin(operator);
        int normalizedLimit = normalizeScanLimit(limit);
        LocalDateTime scanTime = now == null ? LocalDateTime.now() : now;
        List<TicketSlaInstance> candidates = instanceRepository.findBreachedCandidates(scanTime, normalizedLimit);
        List<Long> markedIds = new ArrayList<>();
        for (TicketSlaInstance candidate : candidates) {
            if (candidate.getId() != null && instanceRepository.markBreached(candidate.getId()) > 0) {
                markedIds.add(candidate.getId());
            }
        }
        return TicketSlaScanResultDTO.builder()
                .scanTime(scanTime)
                .limit(normalizedLimit)
                .candidateCount(candidates.size())
                .markedCount(markedIds.size())
                .breachedInstanceIds(markedIds)
                .build();
    }

    private TicketSlaPolicy requirePolicy(Long policyId) {
        TicketSlaPolicy policy = policyRepository.findById(policyId);
        if (policy == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_SLA_POLICY_NOT_FOUND);
        }
        return policy;
    }

    /** 校验 SLA 策略时限。 */
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

    /** 将枚举转换成 code。 */
    private String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }

    /** 将布尔值转换为数据库启停标记。 */
    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }

    /** 归一化扫描批次大小。 */
    private int normalizeScanLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_SCAN_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_SCAN_LIMIT);
    }
}
