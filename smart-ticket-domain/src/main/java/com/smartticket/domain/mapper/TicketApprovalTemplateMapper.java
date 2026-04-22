package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketApprovalTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketApprovalTemplateMapper {

    int insert(TicketApprovalTemplate template);

    int update(TicketApprovalTemplate template);

    TicketApprovalTemplate findById(@Param("id") Long id);

    TicketApprovalTemplate findEnabledByTicketType(@Param("ticketType") String ticketType);

    List<TicketApprovalTemplate> page(@Param("ticketType") String ticketType, @Param("enabled") Integer enabled, @Param("offset") int offset, @Param("limit") int limit);

    long count(@Param("ticketType") String ticketType, @Param("enabled") Integer enabled);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
