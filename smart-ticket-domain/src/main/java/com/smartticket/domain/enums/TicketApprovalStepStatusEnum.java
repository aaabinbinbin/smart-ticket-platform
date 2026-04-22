package com.smartticket.domain.enums;

import java.util.Arrays;

public enum TicketApprovalStepStatusEnum implements CodeInfoEnum {
    WAITING("WAITING", "待到达"),
    PENDING("PENDING", "待处理"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String info;

    TicketApprovalStepStatusEnum(String code, String info) {
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

    public static TicketApprovalStepStatusEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported approval step status code: " + code));
    }
}
