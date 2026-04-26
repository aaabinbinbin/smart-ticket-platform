package com.smartticket.biz.dto.ticket;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单创建命令DTO数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreateCommandDTO {
    // 标题
    private String title;
    // 描述
    private String description;
    // 类型
    private TicketTypeEnum type;
    // 类型画像
    private Map<String, Object> typeProfile;
    // 分类
    private TicketCategoryEnum category;
    // 优先级
    private TicketPriorityEnum priority;
    // 幂等键
    private String idempotencyKey;
    // 来源
    private String source;
}

