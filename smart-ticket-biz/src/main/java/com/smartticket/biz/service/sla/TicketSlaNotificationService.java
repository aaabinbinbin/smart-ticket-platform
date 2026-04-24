package com.smartticket.biz.service.sla;

import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketSlaInstance;

/**
 * 工单SLA通知服务接口定义。
 */
public interface TicketSlaNotificationService {
    /**
     * 处理Breached。
     */
    void notifyBreached(Ticket ticket, TicketSlaInstance instance, String breachType, boolean escalated);
}
