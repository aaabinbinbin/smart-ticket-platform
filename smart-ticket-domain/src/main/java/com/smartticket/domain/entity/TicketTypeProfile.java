package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单类型画像类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeProfile {
    // ID
    private Long id;
    // 工单ID
    private Long ticketId;
    // 画像Schema
    private String profileSchema;
    // 画像Data
    private String profileData;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
