package com.smartticket.biz.repository;

import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.mapper.TicketKnowledgeMapper;
import org.springframework.stereotype.Repository;

/**
 * 工单知识仓储封装。
 *
 * <p>该仓储只负责 ticket_knowledge 表的读写，不做向量化和检索。</p>
 */
@Repository
public class TicketKnowledgeRepository {
    /** 工单知识 MyBatis Mapper。 */
    private final TicketKnowledgeMapper ticketKnowledgeMapper;

    public TicketKnowledgeRepository(TicketKnowledgeMapper ticketKnowledgeMapper) {
        this.ticketKnowledgeMapper = ticketKnowledgeMapper;
    }

    /** 新增知识记录。 */
    public int insert(TicketKnowledge ticketKnowledge) {
        return ticketKnowledgeMapper.insert(ticketKnowledge);
    }

    /** 根据来源工单 ID 查询知识记录。 */
    public TicketKnowledge findByTicketId(Long ticketId) {
        return ticketKnowledgeMapper.findByTicketId(ticketId);
    }

    /** 更新已有知识记录。 */
    public int update(TicketKnowledge ticketKnowledge) {
        return ticketKnowledgeMapper.update(ticketKnowledge);
    }
}
