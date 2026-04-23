package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketKnowledgeCandidateMapper {
    int insert(TicketKnowledgeCandidate candidate);

    int update(TicketKnowledgeCandidate candidate);

    TicketKnowledgeCandidate findById(@Param("id") Long id);

    TicketKnowledgeCandidate findByTicketId(@Param("ticketId") Long ticketId);

    List<TicketKnowledgeCandidate> findByStatus(@Param("status") String status, @Param("limit") int limit);
}
