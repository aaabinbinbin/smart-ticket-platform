package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketApprovalTemplateStep;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketApprovalTemplateStepMapper {

    int insertBatch(@Param("steps") List<TicketApprovalTemplateStep> steps);

    int deleteByTemplateId(@Param("templateId") Long templateId);

    List<TicketApprovalTemplateStep> findByTemplateId(@Param("templateId") Long templateId);
}
