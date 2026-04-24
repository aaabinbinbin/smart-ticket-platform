package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体用户Preference记忆类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUserPreferenceMemory {
    // 用户ID
    private Long userId;
    // common工单类型
    private String commonTicketType;
    // common分类
    private String commonCategory;
    // common优先级
    private String commonPriority;
    // commonTerms
    private String commonTerms;
    // 响应Style
    private String responseStyle;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
