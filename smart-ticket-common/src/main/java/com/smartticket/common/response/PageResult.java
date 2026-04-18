package com.smartticket.common.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询结果。
 *
 * @param <T> 分页记录类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    /** 当前页码，从 1 开始。 */
    private int pageNo;
    /** 每页大小。 */
    private int pageSize;
    /** 总记录数。 */
    private long total;
    /** 当前页数据。 */
    private List<T> records;
}
