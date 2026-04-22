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

    private final String code;
    private final String info;

    TicketCategoryEnum(String code, String info) {
        this.code = code;
        this.info = info;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getInfo() {
        return info;
    }

    public static TicketCategoryEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket category code: " + code));
    }
}
