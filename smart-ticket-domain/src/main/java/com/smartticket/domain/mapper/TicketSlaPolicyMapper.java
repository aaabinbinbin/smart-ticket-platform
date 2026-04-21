package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketSlaPolicy;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单 SLA 策略 Mapper，只负责数据访问。
 */
@Mapper
public interface TicketSlaPolicyMapper {

    int insert(TicketSlaPolicy policy);

    TicketSlaPolicy findById(@Param("id") Long id);

    List<TicketSlaPolicy> page(
            @Param("category") String category,
            @Param("priority") String priority,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(
            @Param("category") String category,
            @Param("priority") String priority,
            @Param("enabled") Integer enabled
    );

    int update(TicketSlaPolicy policy);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    TicketSlaPolicy findBestMatch(@Param("category") String category, @Param("priority") String priority);
}
