package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketTypeProfile;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketTypeProfileMapper {

    int insert(TicketTypeProfile profile);

    int updateByTicketId(@Param("ticketId") Long ticketId, @Param("profileSchema") String profileSchema, @Param("profileData") String profileData);

    TicketTypeProfile findByTicketId(@Param("ticketId") Long ticketId);

    List<TicketTypeProfile> findByTicketIds(@Param("ticketIds") List<Long> ticketIds);
}
