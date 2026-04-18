package com.smartticket.biz.service;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import org.springframework.stereotype.Service;

/**
 * 工单业务权限服务。
 *
 * <p>负责判断当前用户在某张工单上的业务权限，例如查看、转派、解决、关闭。
 * 这里处理的是工单关系权限，不是登录角色认证。</p>
 */
@Service
public class TicketPermissionService {

    public boolean canView(CurrentUser user, Ticket ticket) {
        return user.isAdmin()
                || ticket.getCreatorId().equals(user.getUserId())
                || (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(user.getUserId()));
    }

    public boolean canTransfer(CurrentUser user, Ticket ticket) {
        return user.isAdmin()
                || (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(user.getUserId()));
    }

    public boolean canResolve(CurrentUser user, Ticket ticket) {
        return user.isAdmin()
                || (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(user.getUserId()));
    }

    public boolean canClose(CurrentUser user, Ticket ticket) {
        return user.isAdmin() || ticket.getCreatorId().equals(user.getUserId());
    }

    public void requireView(CurrentUser user, Ticket ticket) {
        if (!canView(user, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_FORBIDDEN);
        }
    }

    public void requireAdmin(CurrentUser user) {
        if (!user.isAdmin()) {
            throw new BusinessException(BusinessErrorCode.ADMIN_REQUIRED);
        }
    }

    public void requireTransfer(CurrentUser user, Ticket ticket) {
        if (!canTransfer(user, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_TRANSFER_FORBIDDEN);
        }
    }

    public void requireResolve(CurrentUser user, Ticket ticket) {
        if (!canResolve(user, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_RESOLVE_FORBIDDEN);
        }
    }

    public void requireClose(CurrentUser user, Ticket ticket) {
        if (!canClose(user, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_CLOSE_FORBIDDEN);
        }
    }
}
