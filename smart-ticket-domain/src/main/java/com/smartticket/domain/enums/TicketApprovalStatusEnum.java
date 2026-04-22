package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * 工单审批状态枚举。
 */
public enum TicketApprovalStatusEnum implements CodeInfoEnum {
    PENDING("PENDING", "待审批"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String info;

    TicketApprovalStatusEnum(String code, String info) {
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

    public static TicketApprovalStatusEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket approval status code: " + code));
    }
}
