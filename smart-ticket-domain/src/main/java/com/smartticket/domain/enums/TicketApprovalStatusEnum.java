package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * 工单审批状态枚举。
 */
public enum TicketApprovalStatusEnum implements CodeInfoEnum {
    PENDING("PENDING", "待审批"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回");

    // 编码
    private final String code;
    // info
    private final String info;

    TicketApprovalStatusEnum(String code, String info) {
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
    public static TicketApprovalStatusEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket approval status code: " + code));
    }
}
