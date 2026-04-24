package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * 工单分类枚举。
 */
public enum TicketCategoryEnum implements CodeInfoEnum {
    ACCOUNT("ACCOUNT", "账号权限"),
    SYSTEM("SYSTEM", "系统功能"),
    ENVIRONMENT("ENVIRONMENT", "环境配置"),
    OTHER("OTHER", "其他");

    // 编码
    private final String code;
    // info
    private final String info;

    TicketCategoryEnum(String code, String info) {
        this.code = code;
        this.info = info;
    }

    /**
     * 获取编码。
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取Info。
     */
    @Override
    public String getInfo() {
        return info;
    }

    /**
     * 处理编码。
     */
    public static TicketCategoryEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket category code: " + code));
    }
}
