package com.smartticket.biz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列分页查询条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueuePageQueryDTO {
    /** 页码，从 1 开始。 */
    private int pageNo;
    /** 每页大小。 */
    private int pageSize;
    /** 所属工单组 ID。 */
    private Long groupId;
    /** 名称或编码关键字。 */
    private String keyword;
    /** 是否启用。 */
    private Boolean enabled;
}
