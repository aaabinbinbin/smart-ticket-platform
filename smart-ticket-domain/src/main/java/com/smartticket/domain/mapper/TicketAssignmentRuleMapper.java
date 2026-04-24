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

    /**
     * 处理新增。
     */
    int insert(TicketAssignmentRule rule);

    /**
     * 查询按ID。
     */
    TicketAssignmentRule findById(@Param("id") Long id);

    /**
     * 分页查询。
     */
    List<TicketAssignmentRule> page(
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
    int update(TicketAssignmentRule rule);

    /**
     * 更新启用。
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    /**
     * 查询最佳匹配结果。
     */
    TicketAssignmentRule findBestMatch(@Param("category") String category, @Param("priority") String priority);
}
