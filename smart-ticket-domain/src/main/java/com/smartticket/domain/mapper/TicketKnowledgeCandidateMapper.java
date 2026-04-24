package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单知识候选映射接口定义。
 */
@Mapper
public interface TicketKnowledgeCandidateMapper {
    /**
     * 处理新增。
     */
    int insert(TicketKnowledgeCandidate candidate);

    /**
     * 更新。
     */
    int update(TicketKnowledgeCandidate candidate);

    /**
     * 查询按ID。
     */
    TicketKnowledgeCandidate findById(@Param("id") Long id);

    /**
     * 查询按工单ID。
     */
    TicketKnowledgeCandidate findByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 查询按状态。
     */
    List<TicketKnowledgeCandidate> findByStatus(@Param("status") String status, @Param("limit") int limit);
}
