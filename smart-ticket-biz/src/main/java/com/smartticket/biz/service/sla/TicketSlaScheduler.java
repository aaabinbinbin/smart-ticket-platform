package com.smartticket.biz.service.sla;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TicketSlaScheduler {
    private final TicketSlaService ticketSlaService;

    public TicketSlaScheduler(TicketSlaService ticketSlaService) {
        this.ticketSlaService = ticketSlaService;
    }

    @Scheduled(
            fixedDelayString = "${smart-ticket.sla.scan.fixed-delay-ms:60000}",
            initialDelayString = "${smart-ticket.sla.scan.initial-delay-ms:10000}"
    )
    public void scanAndEscalate() {
        ticketSlaService.scanBreachedInstancesAutomatically();
    }
}
