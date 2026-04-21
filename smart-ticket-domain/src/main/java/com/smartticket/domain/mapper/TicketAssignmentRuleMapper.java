package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketAssignmentRule;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单自动分派规则 Mapper，只负责数据访问。
 */
@Mapper
public interface TicketAssignmentRuleMapper {

    int insert(TicketAssignmentRule rule);

    TicketAssignmentRule findById(@Param("id") Long id);

    List<TicketAssignmentRule> page(
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

    int update(TicketAssignmentRule rule);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    TicketAssignmentRule findBestMatch(@Param("category") String category, @Param("priority") String priority);
}
