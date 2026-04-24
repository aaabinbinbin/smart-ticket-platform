package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * 工单审批步骤状态枚举定义。
 */
public enum TicketApprovalStepStatusEnum implements CodeInfoEnum {
    WAITING("WAITING", "待到达"),
    PENDING("PENDING", "待处理"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回");

    // 编码
    private final String code;
    // info
    private final String info;

    TicketApprovalStepStatusEnum(String code, String info) {
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
    public static TicketApprovalStepStatusEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported approval step status code: " + code));
    }
}
