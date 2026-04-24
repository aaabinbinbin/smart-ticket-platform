package com.smartticket.biz.repository.type;

import com.smartticket.domain.entity.TicketTypeProfile;
import com.smartticket.domain.mapper.TicketTypeProfileMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单类型画像仓储仓储接口。
 */
@Repository
public class TicketTypeProfileRepository {
    // 工单类型画像映射接口
    private final TicketTypeProfileMapper ticketTypeProfileMapper;

    /**
     * 构造工单类型画像仓储。
     */
    public TicketTypeProfileRepository(TicketTypeProfileMapper ticketTypeProfileMapper) {
        this.ticketTypeProfileMapper = ticketTypeProfileMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketTypeProfile profile) {
        return ticketTypeProfileMapper.insert(profile);
    }

    /**
     * 更新按工单ID。
     */
    public int updateByTicketId(Long ticketId, String profileSchema, String profileData) {
        return ticketTypeProfileMapper.updateByTicketId(ticketId, profileSchema, profileData);
    }

    /**
     * 查询按工单ID。
     */
    public TicketTypeProfile findByTicketId(Long ticketId) {
        return ticketTypeProfileMapper.findByTicketId(ticketId);
    }

    /**
     * 查询按工单Ids。
     */
    public List<TicketTypeProfile> findByTicketIds(List<Long> ticketIds) {
        return ticketTypeProfileMapper.findByTicketIds(ticketIds);
    }
}

