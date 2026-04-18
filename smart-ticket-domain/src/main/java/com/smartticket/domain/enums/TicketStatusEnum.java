package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * 工单状态枚举。
 *
 * <p>当前主流程为：待分配、处理中、已解决、已关闭。</p>
 */
public enum TicketStatusEnum implements CodeInfoEnum {
    PENDING_ASSIGN("PENDING_ASSIGN", "待分配"),
    PROCESSING("PROCESSING", "处理中"),
    RESOLVED("RESOLVED", "已解决"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String info;

    TicketStatusEnum(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

    public static TicketStatusEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket status code: " + code));
    }
}
