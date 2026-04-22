package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * 工单摘要视角枚举。
 */
public enum TicketSummaryViewEnum implements CodeInfoEnum {
    SUBMITTER("SUBMITTER", "提单人视角"),
    ASSIGNEE("ASSIGNEE", "处理人视角"),
    ADMIN("ADMIN", "管理员视角");

    private final String code;
    private final String info;

    TicketSummaryViewEnum(String code, String info) {
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

    public static TicketSummaryViewEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket summary view code: " + code));
    }
}
