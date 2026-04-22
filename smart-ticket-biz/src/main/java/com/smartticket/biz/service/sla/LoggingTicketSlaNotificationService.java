package com.smartticket.biz.service.sla;

import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketSlaInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingTicketSlaNotificationService implements TicketSlaNotificationService {
    private static final Logger log = LoggerFactory.getLogger(LoggingTicketSlaNotificationService.class);

    @Override
    public void notifyBreached(Ticket ticket, TicketSlaInstance instance, String breachType, boolean escalated) {
        log.warn("ticket sla breached: ticketId={}, ticketNo={}, instanceId={}, breachType={}, escalated={}",
                ticket == null ? null : ticket.getId(),
                ticket == null ? null : ticket.getTicketNo(),
                instance == null ? null : instance.getId(),
                breachType,
                escalated);
    }
}
