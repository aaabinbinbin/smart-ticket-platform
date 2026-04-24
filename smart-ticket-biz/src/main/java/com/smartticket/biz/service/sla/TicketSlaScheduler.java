package com.smartticket.biz.service.sla;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 工单SLA调度器类。
 */
@Component
public class TicketSlaScheduler {
    // 工单SLA服务
    private final TicketSlaService ticketSlaService;

    /**
     * 构造工单SLA调度器。
     */
    public TicketSlaScheduler(TicketSlaService ticketSlaService) {
        this.ticketSlaService = ticketSlaService;
    }

    @Scheduled(
            fixedDelayString = "${smart-ticket.sla.scan.fixed-delay-ms:60000}",
            initialDelayString = "${smart-ticket.sla.scan.initial-delay-ms:10000}"
    )
    /**
     * 扫描并Escalate。
     */
    public void scanAndEscalate() {
        ticketSlaService.scanBreachedInstancesAutomatically();
    }
}
