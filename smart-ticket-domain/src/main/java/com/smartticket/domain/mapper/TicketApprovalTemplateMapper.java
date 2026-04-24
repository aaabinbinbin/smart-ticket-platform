package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketApprovalTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单审批模板映射接口定义。
 */
@Mapper
public interface TicketApprovalTemplateMapper {

    /**
     * 处理新增。
     */
    int insert(TicketApprovalTemplate template);

    /**
     * 更新。
     */
    int update(TicketApprovalTemplate template);

    /**
     * 查询按ID。
     */
    TicketApprovalTemplate findById(@Param("id") Long id);

    /**
     * 查询启用按工单类型。
     */
    TicketApprovalTemplate findEnabledByTicketType(@Param("ticketType") String ticketType);

    /**
     * 分页查询。
     */
    List<TicketApprovalTemplate> page(@Param("ticketType") String ticketType, @Param("enabled") Integer enabled, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 获取统计信息。
     */
    long count(@Param("ticketType") String ticketType, @Param("enabled") Integer enabled);

    /**
     * 更新启用。
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
