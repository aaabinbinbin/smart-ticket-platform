package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * 工单类型枚举。
 *
 * <p>用于区分事件、权限申请、环境申请、咨询和变更等不同处理流。</p>
 */
public enum TicketTypeEnum implements CodeInfoEnum {
    INCIDENT("INCIDENT", "故障事件"),
    ACCESS_REQUEST("ACCESS_REQUEST", "权限申请"),
    ENVIRONMENT_REQUEST("ENVIRONMENT_REQUEST", "环境申请"),
    CONSULTATION("CONSULTATION", "咨询请求"),
    CHANGE_REQUEST("CHANGE_REQUEST", "变更申请");

    // 编码
    private final String code;
    // info
    private final String info;

    TicketTypeEnum(String code, String info) {
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
    public static TicketTypeEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket type code: " + code));
    }
}
