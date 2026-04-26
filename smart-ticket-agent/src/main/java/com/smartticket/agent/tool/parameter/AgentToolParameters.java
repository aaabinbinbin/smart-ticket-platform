package com.smartticket.agent.tool.parameter;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Tool 结构化参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolParameters {
    // 工单ID
    private Long ticketId;
    // 处理人ID
    private Long assigneeId;
    // 标题
    private String title;
    // 描述
    private String description;
    // 幂等键
    private String idempotencyKey;
    // 类型
    private TicketTypeEnum type;
    // 分类
    private TicketCategoryEnum category;
    // 优先级
    private TicketPriorityEnum priority;
    // 类型画像
    private Map<String, Object> typeProfile;
    // 摘要Requested
    private Boolean summaryRequested;
    // 摘要View
    private TicketSummaryViewEnum summaryView;
    @Builder.Default
    private List<Long> numbers = new ArrayList<>();
}
