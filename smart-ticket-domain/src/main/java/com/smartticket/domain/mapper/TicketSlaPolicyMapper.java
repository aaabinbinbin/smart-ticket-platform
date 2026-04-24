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

    /**
     * 处理新增。
     */
    int insert(TicketSlaPolicy policy);

    /**
     * 查询按ID。
     */
    TicketSlaPolicy findById(@Param("id") Long id);

    /**
     * 分页查询。
     */
    List<TicketSlaPolicy> page(
            @Param("category") String category,
            @Param("priority") String priority,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 获取统计信息。
     */
    long count(
            @Param("category") String category,
            @Param("priority") String priority,
            @Param("enabled") Integer enabled
    );

    /**
     * 更新。
     */
    int update(TicketSlaPolicy policy);

    /**
     * 更新启用。
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    /**
     * 查询最佳匹配结果。
     */
    TicketSlaPolicy findBestMatch(@Param("category") String category, @Param("priority") String priority);
}
