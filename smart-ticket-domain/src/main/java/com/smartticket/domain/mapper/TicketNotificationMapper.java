package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketNotification;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单通知映射接口定义。
 */
@Mapper
public interface TicketNotificationMapper {

    /**
     * 处理新增。
     */
    int insert(TicketNotification notification);

    /**
     * 查询按ID。
     */
    TicketNotification findById(@Param("id") Long id);

    /**
     * 分页查询按Receiver用户ID。
     */
    List<TicketNotification> pageByReceiverUserId(
            @Param("receiverUserId") Long receiverUserId,
            @Param("unreadOnly") Boolean unreadOnly,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 统计按Receiver用户ID。
     */
    long countByReceiverUserId(@Param("receiverUserId") Long receiverUserId, @Param("unreadOnly") Boolean unreadOnly);

    /**
     * 读取数据。
     */
    int markRead(@Param("id") Long id, @Param("receiverUserId") Long receiverUserId, @Param("readAt") LocalDateTime readAt);
}
