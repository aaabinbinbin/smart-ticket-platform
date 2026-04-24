package com.smartticket.rag.repository;

import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.mapper.TicketKnowledgeMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * RAG 模块的知识读取仓储。
 *
 * <p>该仓储只读取 ticket_knowledge，用于检索结果补充摘要和来源工单信息，不负责知识构建。</p>
 */
@Repository
public class TicketKnowledgeReadRepository {
    /** 工单知识 Mapper。 */
    private final TicketKnowledgeMapper mapper;

    /**
     * 构造工单知识Read仓储。
     */
    public TicketKnowledgeReadRepository(TicketKnowledgeMapper mapper) {
        this.mapper = mapper;
    }

    /** 根据知识 ID 查询知识记录。 */
    public TicketKnowledge findById(Long id) {
        return mapper.findById(id);
    }

    /** 查询所有可用知识。 */
    public List<TicketKnowledge> findActive() {
        return mapper.findActive();
    }
}
