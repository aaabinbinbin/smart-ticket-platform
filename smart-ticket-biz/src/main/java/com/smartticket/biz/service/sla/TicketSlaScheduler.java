package com.smartticket.biz.service.sla;

import com.smartticket.biz.dto.sla.TicketSlaScanResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 工单SLA调度器类，定时扫描违约实例并升级，异常时自愈恢复。
 */
@Component
public class TicketSlaScheduler {
    private static final Logger log = LoggerFactory.getLogger(TicketSlaScheduler.class);

    private final TicketSlaService ticketSlaService;

    public TicketSlaScheduler(TicketSlaService ticketSlaService) {
        this.ticketSlaService = ticketSlaService;
    }

    @Scheduled(
            fixedDelayString = "${smart-ticket.sla.scan.fixed-delay-ms:60000}",
            initialDelayString = "${smart-ticket.sla.scan.initial-delay-ms:10000}"
    )
    public void scanAndEscalate() {
        try {
            TicketSlaScanResultDTO result = ticketSlaService.scanBreachedInstancesAutomatically();
            log.info("SLA 扫描完成: candidates={}, marked={}, escalated={}, errors={}",
                    result.getCandidateCount(), result.getMarkedCount(),
                    result.getEscalatedCount(),
                    result.getBreachedInstanceIds().size() - result.getMarkedCount());
        } catch (Exception ex) {
            log.error("SLA 扫描调度异常，将在下一轮重试: {}", ex.getMessage(), ex);
        }
    }
}
