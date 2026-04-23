package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketNotification;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketNotificationMapper {

    int insert(TicketNotification notification);

    TicketNotification findById(@Param("id") Long id);

    List<TicketNotification> pageByReceiverUserId(
            @Param("receiverUserId") Long receiverUserId,
            @Param("unreadOnly") Boolean unreadOnly,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByReceiverUserId(@Param("receiverUserId") Long receiverUserId, @Param("unreadOnly") Boolean unreadOnly);

    int markRead(@Param("id") Long id, @Param("receiverUserId") Long receiverUserId, @Param("readAt") LocalDateTime readAt);
}
