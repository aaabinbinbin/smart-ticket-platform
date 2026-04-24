package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * 工单优先级枚举。
 *
 * <p>用于表达问题处理优先程度，前后端通过 code 交互。</p>
 */
public enum TicketPriorityEnum implements CodeInfoEnum {
    LOW("LOW", "低"),
    MEDIUM("MEDIUM", "中"),
    HIGH("HIGH", "高"),
    URGENT("URGENT", "紧急");

    // 编码
    private final String code;
    // info
    private final String info;

    TicketPriorityEnum(String code, String info) {
        this.code = code;
        this.info = info;
    }

    /**
     * 获取编码。
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取Info。
     */
    public String getInfo() {
        return info;
    }

    /**
     * 处理编码。
     */
    public static TicketPriorityEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket priority code: " + code));
    }
}
