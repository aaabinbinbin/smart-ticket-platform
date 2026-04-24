package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketGroup;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单组 Mapper，只负责数据访问，不承载业务权限和规则。
 */
@Mapper
public interface TicketGroupMapper {

    /**
     * 处理新增。
     */
    int insert(TicketGroup ticketGroup);

    /**
     * 查询按ID。
     */
    TicketGroup findById(@Param("id") Long id);

    /**
     * 查询按编码。
     */
    TicketGroup findByCode(@Param("groupCode") String groupCode);

    /**
     * 分页查询。
     */
    List<TicketGroup> page(
            @Param("keyword") String keyword,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 获取统计信息。
     */
    long count(@Param("keyword") String keyword, @Param("enabled") Integer enabled);

    /**
     * 更新。
     */
    int update(TicketGroup ticketGroup);

    /**
     * 更新启用。
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
