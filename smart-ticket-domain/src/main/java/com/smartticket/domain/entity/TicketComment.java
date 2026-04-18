package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单评论实体，对应表 {@code ticket_comment}。
 *
 * <p>评论用于保存协作过程中的用户回复、处理记录和解决方案补充。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketComment {
    /** 评论主键。 */
    private Long id;
    /** 所属工单 ID。 */
    private Long ticketId;
    /** 评论人用户 ID。 */
    private Long commenterId;
    /** 评论类型，例如 USER_REPLY、PROCESS_LOG、SOLUTION。 */
    private String commentType;
    /** 评论正文。 */
    private String content;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
