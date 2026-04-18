package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单附件实体，对应表 {@code ticket_attachment}。
 *
 * <p>MVP 阶段只保存文件访问地址，不在数据库中保存文件二进制内容。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAttachment {
    /** 附件主键。 */
    private Long id;
    /** 所属工单 ID。 */
    private Long ticketId;
    /** 原始文件名。 */
    private String fileName;
    /** 文件访问地址或对象存储地址。 */
    private String fileUrl;
    /** 文件类型或扩展名。 */
    private String fileType;
    /** 文件大小，单位字节。 */
    private Long fileSize;
    /** 上传人用户 ID。 */
    private Long uploaderId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
