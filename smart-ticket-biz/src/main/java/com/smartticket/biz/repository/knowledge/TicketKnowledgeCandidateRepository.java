package com.smartticket.biz.repository.knowledge;

import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import com.smartticket.domain.mapper.TicketKnowledgeCandidateMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TicketKnowledgeCandidateRepository {
    private final TicketKnowledgeCandidateMapper mapper;

    public TicketKnowledgeCandidateRepository(TicketKnowledgeCandidateMapper mapper) {
        this.mapper = mapper;
    }

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

    public TicketKnowledgeCandidate findByTicketId(Long ticketId) {
        return mapper.findByTicketId(ticketId);
    }

    public TicketKnowledgeCandidate findById(Long id) {
        return mapper.findById(id);
    }

    public List<TicketKnowledgeCandidate> findByStatus(String status, int limit) {
        return mapper.findByStatus(status, limit);
    }
}
