package com.smartticket.biz.service.ticket;

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

    /**
     * 处理视图类型。
     */
    public boolean canView(CurrentUser user, Ticket ticket) {
        return user.isAdmin()
                || ticket.getCreatorId().equals(user.getUserId())
                || (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(user.getUserId()));
    }

    /**
     * 处理转派。
     */
    public boolean canTransfer(CurrentUser user, Ticket ticket) {
        return user.isAdmin()
                || (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(user.getUserId()));
    }

    /**
     * 处理解析。
     */
    public boolean canResolve(CurrentUser user, Ticket ticket) {
        return user.isAdmin()
                || (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(user.getUserId()));
    }

    /**
     * 处理关闭。
     */
    public boolean canClose(CurrentUser user, Ticket ticket) {
        return user.isAdmin() || ticket.getCreatorId().equals(user.getUserId());
    }

    /**
     * 校验管理。
     */
    public void requireAdmin(CurrentUser user) {
        if (!user.isAdmin()) {
            throw new BusinessException(BusinessErrorCode.ADMIN_REQUIRED);
        }
    }

    /**
     * 校验转派。
     */
    public void requireTransfer(CurrentUser user, Ticket ticket) {
        if (!canTransfer(user, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_TRANSFER_FORBIDDEN);
        }
    }

    /**
     * 校验解析。
     */
    public void requireResolve(CurrentUser user, Ticket ticket) {
        if (!canResolve(user, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_RESOLVE_FORBIDDEN);
        }
    }

    /**
     * 校验关闭。
     */
    public void requireClose(CurrentUser user, Ticket ticket) {
        if (!canClose(user, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_CLOSE_FORBIDDEN);
        }
    }
}

