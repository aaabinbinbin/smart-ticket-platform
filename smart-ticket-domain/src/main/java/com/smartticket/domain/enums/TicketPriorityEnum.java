package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * Ticket priority.
 */
public enum TicketPriorityEnum implements CodeInfoEnum {
    LOW("LOW", "低"),
    MEDIUM("MEDIUM", "中"),
    HIGH("HIGH", "高"),
    URGENT("URGENT", "紧急");

    private final String code;
    private final String info;

    TicketPriorityEnum(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

    public static TicketPriorityEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ticket priority code: " + code));
    }
}
