package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledgeEmbedding;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Ticket knowledge embedding mapper for table {@code ticket_knowledge_embedding}.
 */
@Mapper
public interface TicketKnowledgeEmbeddingMapper {

    int insert(TicketKnowledgeEmbedding ticketKnowledgeEmbedding);

    TicketKnowledgeEmbedding findById(@Param("id") Long id);

    List<TicketKnowledgeEmbedding> findByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
