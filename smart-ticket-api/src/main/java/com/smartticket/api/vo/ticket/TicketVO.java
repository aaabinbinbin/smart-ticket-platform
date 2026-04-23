package com.smartticket.api.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工单响应对象")
public class TicketVO {
    private Long id;
    private String ticketNo;
    private String title;
    private String description;
    private String type;
    private String typeInfo;
    private Map<String, Object> typeProfile;
    private String category;
    private String categoryInfo;
    private String priority;
    private String priorityInfo;
    private String status;
    private String statusInfo;
    private Long creatorId;
    private Long assigneeId;
    private Long groupId;
    private Long queueId;
    private String solutionSummary;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
