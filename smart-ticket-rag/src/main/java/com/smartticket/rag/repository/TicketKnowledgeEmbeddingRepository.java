package com.smartticket.rag.repository;

import com.smartticket.domain.entity.TicketKnowledgeEmbedding;
import com.smartticket.domain.mapper.TicketKnowledgeEmbeddingMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单知识切片仓储封装。
 *
 * <p>RAG 模块通过该仓储写入切片和向量，不反向修改工单业务事实。</p>
 */
@Repository
public class TicketKnowledgeEmbeddingRepository {
    /** 工单知识切片 Mapper。 */
    private final TicketKnowledgeEmbeddingMapper mapper;

    public TicketKnowledgeEmbeddingRepository(TicketKnowledgeEmbeddingMapper mapper) {
        this.mapper = mapper;
    }

    /** 新增一条知识切片向量记录。 */
    public int insert(TicketKnowledgeEmbedding embedding) {
        return mapper.insert(embedding);
    }

    /** 查询某条知识下的所有切片。 */
    public List<TicketKnowledgeEmbedding> findByKnowledgeId(Long knowledgeId) {
        return mapper.findByKnowledgeId(knowledgeId);
    }

    /** 删除某条知识已有切片，用于重新构建时保持幂等。 */
    public int deleteByKnowledgeId(Long knowledgeId) {
        return mapper.deleteByKnowledgeId(knowledgeId);
    }
}
