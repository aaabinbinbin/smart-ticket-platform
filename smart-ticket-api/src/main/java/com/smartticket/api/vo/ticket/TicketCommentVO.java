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
    // ID
    private Long id;
    // 工单ID
    private Long ticketId;
    // commenterID
    private Long commenterId;
    // 评论类型
    private String commentType;
    // 内容
    private String content;
    // 创建时间
    private LocalDateTime createdAt;
}
