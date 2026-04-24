package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketTypeProfile;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单类型画像映射接口定义。
 */
@Mapper
public interface TicketTypeProfileMapper {

    /**
     * 处理新增。
     */
    int insert(TicketTypeProfile profile);

    /**
     * 更新按工单ID。
     */
    int updateByTicketId(@Param("ticketId") Long ticketId, @Param("profileSchema") String profileSchema, @Param("profileData") String profileData);

    /**
     * 查询按工单ID。
     */
    TicketTypeProfile findByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 查询按工单Ids。
     */
    List<TicketTypeProfile> findByTicketIds(@Param("ticketIds") List<Long> ticketIds);
}
