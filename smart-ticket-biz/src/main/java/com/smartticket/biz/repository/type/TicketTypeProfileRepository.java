package com.smartticket.biz.repository.type;

import com.smartticket.domain.entity.TicketTypeProfile;
import com.smartticket.domain.mapper.TicketTypeProfileMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TicketTypeProfileRepository {
    private final TicketTypeProfileMapper ticketTypeProfileMapper;

    public TicketTypeProfileRepository(TicketTypeProfileMapper ticketTypeProfileMapper) {
        this.ticketTypeProfileMapper = ticketTypeProfileMapper;
    }

    public int insert(TicketTypeProfile profile) {
        return ticketTypeProfileMapper.insert(profile);
    }

    public int updateByTicketId(Long ticketId, String profileSchema, String profileData) {
        return ticketTypeProfileMapper.updateByTicketId(ticketId, profileSchema, profileData);
    }

    public TicketTypeProfile findByTicketId(Long ticketId) {
        return ticketTypeProfileMapper.findByTicketId(ticketId);
    }

    public List<TicketTypeProfile> findByTicketIds(List<Long> ticketIds) {
        return ticketTypeProfileMapper.findByTicketIds(ticketIds);
    }
}

