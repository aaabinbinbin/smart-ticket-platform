package com.smartticket.api.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单评论响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工单评论响应对象")
public class TicketCommentVO {
    private Long id;
    private Long ticketId;
    private Long commenterId;
    private String commentType;
    private String content;
    private LocalDateTime createdAt;
}
