package com.smartticket.domain.entity;

import com.smartticket.domain.enums.MemorySource;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体用户偏好记忆类。
 *
 * <p>记忆不是权威事实源，数据库/Tool 实时查询结果才是权威事实。
 * 记忆用于上下文缓存和偏好线索，不替代数据库查询。</p>
 *
 * <p>每条记忆包含来源(source)、置信度(confidence)和过期时间(expiresAt)，用于判断可信度。
 * 低置信度记忆只能用于推荐，不能自动执行。</p>
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

    // ========== 可靠性元数据 ==========

    /** 记忆来源类型。 */
    private MemorySource source;

    /** 置信度（0-1），越低越只能用于推荐，不能自动执行。 */
    private Double confidence;

    /** 过期时间，过期后不应使用此记忆。 */
    private LocalDateTime expiresAt;
}
