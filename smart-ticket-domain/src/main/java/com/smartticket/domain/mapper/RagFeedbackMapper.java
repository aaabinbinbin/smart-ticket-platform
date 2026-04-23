package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.RagFeedback;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RagFeedbackMapper {
    int insert(RagFeedback feedback);

    List<RagFeedback> findByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    List<Map<String, Object>> countByKnowledge();

    List<Map<String, Object>> countByFeedbackType();

    List<Map<String, Object>> countFailedQueries();

    List<Map<String, Object>> scoreByKnowledge();
}
