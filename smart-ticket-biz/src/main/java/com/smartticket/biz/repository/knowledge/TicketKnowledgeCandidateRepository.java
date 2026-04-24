package com.smartticket.biz.repository.knowledge;

import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import com.smartticket.domain.mapper.TicketKnowledgeCandidateMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单知识候选仓储仓储接口。
 */
@Repository
public class TicketKnowledgeCandidateRepository {
    // 映射接口
    private final TicketKnowledgeCandidateMapper mapper;

    /**
     * 构造工单知识候选仓储。
     */
    public TicketKnowledgeCandidateRepository(TicketKnowledgeCandidateMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 处理保存。
     */
    public TicketKnowledgeCandidate save(TicketKnowledgeCandidate candidate) {
        TicketKnowledgeCandidate existing = mapper.findByTicketId(candidate.getTicketId());
        if (existing == null) {
            mapper.insert(candidate);
            return candidate;
        }
        candidate.setId(existing.getId());
        mapper.update(candidate);
        return candidate;
    }

    /**
     * 查询按工单ID。
     */
    public TicketKnowledgeCandidate findByTicketId(Long ticketId) {
        return mapper.findByTicketId(ticketId);
    }

    /**
     * 查询按ID。
     */
    public TicketKnowledgeCandidate findById(Long id) {
        return mapper.findById(id);
    }

    /**
     * 查询按状态。
     */
    public List<TicketKnowledgeCandidate> findByStatus(String status, int limit) {
        return mapper.findByStatus(status, limit);
    }
}
