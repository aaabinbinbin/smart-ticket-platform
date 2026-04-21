package com.smartticket.biz.service;

import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketSlaInstance;

public interface TicketSlaNotificationService {
    void notifyBreached(Ticket ticket, TicketSlaInstance instance, String breachType, boolean escalated);
}