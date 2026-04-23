package com.smartticket.biz.repository.notification;

import com.smartticket.domain.entity.TicketNotification;
import com.smartticket.domain.mapper.TicketNotificationMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TicketNotificationRepository {
    private final TicketNotificationMapper ticketNotificationMapper;

    public TicketNotificationRepository(TicketNotificationMapper ticketNotificationMapper) {
        this.ticketNotificationMapper = ticketNotificationMapper;
    }

    public int insert(TicketNotification notification) {
        return ticketNotificationMapper.insert(notification);
    }

    public TicketNotification findById(Long id) {
        return ticketNotificationMapper.findById(id);
    }

    public List<TicketNotification> pageByReceiverUserId(Long receiverUserId, Boolean unreadOnly, int offset, int limit) {
        return ticketNotificationMapper.pageByReceiverUserId(receiverUserId, unreadOnly, offset, limit);
    }

    public long countByReceiverUserId(Long receiverUserId, Boolean unreadOnly) {
        return ticketNotificationMapper.countByReceiverUserId(receiverUserId, unreadOnly);
    }

    public int markRead(Long id, Long receiverUserId, LocalDateTime readAt) {
        return ticketNotificationMapper.markRead(id, receiverUserId, readAt);
    }
}
