package com.smartticket.agent.tool.parameter;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import java.util.ArrayList;
import java.util.List;
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
    private Long ticketId;
    private Long assigneeId;
    private String title;
    private String description;
    private TicketCategoryEnum category;
    private TicketPriorityEnum priority;
    @Builder.Default
    private List<Long> numbers = new ArrayList<>();
}
