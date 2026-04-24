package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledgeEmbedding;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单知识切片 Mapper，对应表 {@code ticket_knowledge_embedding}。
 */
@Mapper
public interface TicketKnowledgeEmbeddingMapper {

    /**
     * 处理新增。
     */
    int insert(TicketKnowledgeEmbedding ticketKnowledgeEmbedding);

    /**
     * 查询按ID。
     */
    TicketKnowledgeEmbedding findById(@Param("id") Long id);

    /**
     * 查询按知识ID。
     */
    List<TicketKnowledgeEmbedding> findByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 查询全部。
     */
    List<TicketKnowledgeEmbedding> findAll();

    /**
     * 删除按知识ID。
     */
    int deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
