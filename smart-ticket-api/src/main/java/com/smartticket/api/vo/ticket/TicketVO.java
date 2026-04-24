package com.smartticket.api.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单VO视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工单响应对象")
public class TicketVO {
    // ID
    private Long id;
    // 工单编号
    private String ticketNo;
    // 标题
    private String title;
    // 描述
    private String description;
    // 类型
    private String type;
    // 类型Info
    private String typeInfo;
    // 类型画像
    private Map<String, Object> typeProfile;
    // 分类
    private String category;
    // 分类Info
    private String categoryInfo;
    // 优先级
    private String priority;
    // 优先级Info
    private String priorityInfo;
    // 状态
    private String status;
    // 状态Info
    private String statusInfo;
    // 创建人ID
    private Long creatorId;
    // 处理人ID
    private Long assigneeId;
    // 分组ID
    private Long groupId;
    // 队列ID
    private Long queueId;
    // 解决摘要
    private String solutionSummary;
    // 来源
    private String source;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
