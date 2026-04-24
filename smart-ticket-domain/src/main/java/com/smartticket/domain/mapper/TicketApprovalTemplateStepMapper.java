package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketApprovalTemplateStep;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单审批模板步骤映射接口定义。
 */
@Mapper
public interface TicketApprovalTemplateStepMapper {

    /**
     * 新增Batch。
     */
    int insertBatch(@Param("steps") List<TicketApprovalTemplateStep> steps);

    /**
     * 删除按模板ID。
     */
    int deleteByTemplateId(@Param("templateId") Long templateId);

    /**
     * 查询按模板ID。
     */
    List<TicketApprovalTemplateStep> findByTemplateId(@Param("templateId") Long templateId);
}
