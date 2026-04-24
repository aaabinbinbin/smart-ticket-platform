package com.smartticket.biz.service.ticket;

import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 工单用户目录服务。
 */
@Service
public class TicketUserDirectoryService {
    private static final Set<String> APPROVER_ROLES = Set.of("STAFF", "ADMIN");

    // 工单仓储
    private final TicketRepository ticketRepository;

    /**
     * 构造工单用户目录服务。
     */
    public TicketUserDirectoryService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * 校验启用用户。
     */
    public SysUser requireEnabledUser(Long userId, BusinessErrorCode notFoundCode) {
        SysUser user = ticketRepository.findUserById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(notFoundCode);
        }
        return user;
    }

    /**
     * 校验Staff用户。
     */
    public void requireStaffUser(Long userId) {
        requireEnabledUser(userId, BusinessErrorCode.ASSIGNEE_NOT_FOUND);
        if (!hasRole(userId, "STAFF")) {
            throw new BusinessException(BusinessErrorCode.ASSIGNEE_NOT_STAFF);
        }
    }

    /**
     * 校验审批人用户。
     */
    public void requireApproverUser(Long userId) {
        requireEnabledUser(userId, BusinessErrorCode.ASSIGNEE_NOT_FOUND);
        if (!hasAnyRole(userId, APPROVER_ROLES)) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批人必须具备 STAFF 或 ADMIN 角色");
        }
    }

    /**
     * 处理Staff用户。
     */
    public boolean isStaffUser(Long userId) {
        return userId != null && hasRole(userId, "STAFF");
    }

    /**
     * 处理角色。
     */
    private boolean hasRole(Long userId, String roleCode) {
        return ticketRepository.findRolesByUserId(userId)
                .stream()
                .map(SysRole::getRoleCode)
                .anyMatch(roleCode::equals);
    }

    /**
     * 处理任意角色。
     */
    private boolean hasAnyRole(Long userId, Set<String> roleCodes) {
        return ticketRepository.findRolesByUserId(userId)
                .stream()
                .map(SysRole::getRoleCode)
                .anyMatch(roleCodes::contains);
    }
}
