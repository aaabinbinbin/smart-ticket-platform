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

    int insert(TicketGroup ticketGroup);

    TicketGroup findById(@Param("id") Long id);

    TicketGroup findByCode(@Param("groupCode") String groupCode);

    List<TicketGroup> page(
            @Param("keyword") String keyword,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(@Param("keyword") String keyword, @Param("enabled") Integer enabled);

    int update(TicketGroup ticketGroup);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
