package com.smartticket.biz.repository.notification;

import com.smartticket.domain.entity.TicketNotification;
import com.smartticket.domain.mapper.TicketNotificationMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单通知仓储仓储接口。
 */
@Repository
public class TicketNotificationRepository {
    // 工单通知映射接口
    private final TicketNotificationMapper ticketNotificationMapper;

    /**
     * 构造工单通知仓储。
     */
    public TicketNotificationRepository(TicketNotificationMapper ticketNotificationMapper) {
        this.ticketNotificationMapper = ticketNotificationMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketNotification notification) {
        return ticketNotificationMapper.insert(notification);
    }

    /**
     * 查询按ID。
     */
    public TicketNotification findById(Long id) {
        return ticketNotificationMapper.findById(id);
    }

    /**
     * 分页查询按Receiver用户ID。
     */
    public List<TicketNotification> pageByReceiverUserId(Long receiverUserId, Boolean unreadOnly, int offset, int limit) {
        return ticketNotificationMapper.pageByReceiverUserId(receiverUserId, unreadOnly, offset, limit);
    }

    /**
     * 统计按Receiver用户ID。
     */
    public long countByReceiverUserId(Long receiverUserId, Boolean unreadOnly) {
        return ticketNotificationMapper.countByReceiverUserId(receiverUserId, unreadOnly);
    }

    /**
     * 读取数据。
     */
    public int markRead(Long id, Long receiverUserId, LocalDateTime readAt) {
        return ticketNotificationMapper.markRead(id, receiverUserId, readAt);
    }
}
