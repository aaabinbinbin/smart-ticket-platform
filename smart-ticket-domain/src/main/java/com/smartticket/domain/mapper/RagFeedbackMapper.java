package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.RagFeedback;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 检索增强反馈映射接口定义。
 */
@Mapper
public interface RagFeedbackMapper {
    /**
     * 处理新增。
     */
    int insert(RagFeedback feedback);

    /**
     * 查询按知识ID。
     */
    List<RagFeedback> findByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 统计按知识。
     */
    List<Map<String, Object>> countByKnowledge();

    /**
     * 统计按反馈类型。
     */
    List<Map<String, Object>> countByFeedbackType();

    /**
     * 统计FailedQueries。
     */
    List<Map<String, Object>> countFailedQueries();

    /**
     * 处理按知识。
     */
    List<Map<String, Object>> scoreByKnowledge();
}
