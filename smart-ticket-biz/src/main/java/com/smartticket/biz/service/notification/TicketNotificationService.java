package com.smartticket.biz.service.notification;

import com.smartticket.biz.dto.notification.TicketNotificationPageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.notification.TicketNotificationRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketNotification;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单通知服务。
 */
@Service
public class TicketNotificationService {
    // 编号
    private static final int DEFAULT_PAGE_NO = 1;
    // SIZE
    private static final int DEFAULT_PAGE_SIZE = 10;
    // SIZE
    private static final int MAX_PAGE_SIZE = 100;
    // 应用
    private static final String CHANNEL_IN_APP = "IN_APP";
    // BREACH
    private static final String TYPE_SLA_BREACH = "SLA_BREACH";

    // 工单通知仓储
    private final TicketNotificationRepository ticketNotificationRepository;

    /**
     * 构造工单通知服务。
     */
    public TicketNotificationService(TicketNotificationRepository ticketNotificationRepository) {
        this.ticketNotificationRepository = ticketNotificationRepository;
    }

    /**
     * 创建SLABreach通知。
     */
    @Transactional
    public TicketNotification createSlaBreachNotification(Long ticketId, Long receiverUserId, String title, String content) {
        TicketNotification notification = TicketNotification.builder()
                .ticketId(ticketId)
                .receiverUserId(receiverUserId)
                .channel(CHANNEL_IN_APP)
                .notificationType(TYPE_SLA_BREACH)
                .title(title)
                .content(content)
                .readStatus(0)
                .build();
        ticketNotificationRepository.insert(notification);
        return ticketNotificationRepository.findById(notification.getId());
    }

    /**
     * 分页查询我的通知中心。
     */
    public PageResult<TicketNotification> pageMy通知中心(CurrentUser currentUser, TicketNotificationPageQueryDTO query) {
        int pageNo = query == null || query.getPageNo() == null ? DEFAULT_PAGE_NO : Math.max(query.getPageNo(), 1);
        int pageSize = query == null || query.getPageSize() == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(query.getPageSize(), 1), MAX_PAGE_SIZE);
        Boolean unreadOnly = query == null ? null : query.getUnreadOnly();
        int offset = (pageNo - 1) * pageSize;
        List<TicketNotification> records = ticketNotificationRepository.pageByReceiverUserId(
                currentUser.getUserId(),
                unreadOnly,
                offset,
                pageSize
        );
        long total = ticketNotificationRepository.countByReceiverUserId(currentUser.getUserId(), unreadOnly);
        return PageResult.<TicketNotification>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    /**
     * 读取数据。
     */
    @Transactional
    public TicketNotification markRead(CurrentUser currentUser, Long notificationId) {
        TicketNotification notification = requireOwnedNotification(currentUser, notificationId);
        if (!Integer.valueOf(1).equals(notification.getReadStatus())) {
            ticketNotificationRepository.markRead(notificationId, currentUser.getUserId(), LocalDateTime.now());
            notification = requireOwnedNotification(currentUser, notificationId);
        }
        return notification;
    }

    /**
     * 校验Owned通知。
     */
    private TicketNotification requireOwnedNotification(CurrentUser currentUser, Long notificationId) {
        TicketNotification notification = ticketNotificationRepository.findById(notificationId);
        if (notification == null || !currentUser.getUserId().equals(notification.getReceiverUserId())) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOTIFICATION_NOT_FOUND);
        }
        return notification;
    }
}
