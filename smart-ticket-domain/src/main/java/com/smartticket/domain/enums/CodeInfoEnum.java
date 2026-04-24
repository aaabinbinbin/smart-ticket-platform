package com.smartticket.domain.enums;

/**
 * 枚举 code 和 info 约定。
 *
 * <p>code 用于前后端交互和数据库存储，info 用于给人阅读的中文描述。</p>
 */
public interface CodeInfoEnum {

    /**
     * 获取编码。
     */
    String getCode();

    /**
     * 获取Info。
     */
    String getInfo();
}
