package com.smartticket.biz.service.sla;

import com.smartticket.biz.service.notification.TicketNotificationService;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketSlaInstance;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class InAppTicketSlaNotificationService implements TicketSlaNotificationService {
    private final TicketNotificationService ticketNotificationService;

    public InAppTicketSlaNotificationService(TicketNotificationService ticketNotificationService) {
        this.ticketNotificationService = ticketNotificationService;
    }

    @Override
    public void notifyBreached(Ticket ticket, TicketSlaInstance instance, String breachType, boolean escalated) {
        if (ticket == null || ticket.getId() == null) {
            return;
        }
        Set<Long> receivers = new LinkedHashSet<>();
        if (ticket.getCreatorId() != null) {
            receivers.add(ticket.getCreatorId());
        }
        if (ticket.getAssigneeId() != null) {
            receivers.add(ticket.getAssigneeId());
        }
        if (receivers.isEmpty()) {
            return;
        }
        String title = buildTitle(ticket, breachType);
        String content = buildContent(ticket, instance, breachType, escalated);
        for (Long receiverUserId : receivers) {
            ticketNotificationService.createSlaBreachNotification(ticket.getId(), receiverUserId, title, content);
        }
    }

    private String buildTitle(Ticket ticket, String breachType) {
        String ticketNo = ticket.getTicketNo() == null ? String.valueOf(ticket.getId()) : ticket.getTicketNo();
        if ("FIRST_RESPONSE".equals(breachType)) {
            return "工单 " + ticketNo + " 首次响应超时";
        }
        return "工单 " + ticketNo + " 解决时限超时";
    }

    private String buildContent(Ticket ticket, TicketSlaInstance instance, String breachType, boolean escalated) {
        StringBuilder builder = new StringBuilder();
        builder.append("工单【")
                .append(ticket.getTitle() == null ? ticket.getTicketNo() : ticket.getTitle())
                .append("】发生 SLA 违约。");
        if ("FIRST_RESPONSE".equals(breachType)) {
            builder.append("违约类型：首次响应超时。");
        } else {
            builder.append("违约类型：解决时限超时。");
        }
        if (instance != null && instance.getId() != null) {
            builder.append("SLA 实例 ID：").append(instance.getId()).append("。");
        }
        if (escalated) {
            builder.append("系统已执行升级处理。");
        }
        return builder.toString();
    }
}
