package com.smartticket.domain.enums;

import java.util.Arrays;

/**
 * Ticket operation type.
 */
public enum OperationTypeEnum implements CodeInfoEnum {
    CREATE("CREATE", "创建工单"),
    ASSIGN("ASSIGN", "分配处理人"),
    TRANSFER("TRANSFER", "转派处理人"),
    UPDATE_STATUS("UPDATE_STATUS", "更新状态"),
    COMMENT("COMMENT", "添加评论"),
    CLOSE("CLOSE", "关闭工单");

    private final String code;
    private final String info;

    OperationTypeEnum(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

    public static OperationTypeEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported operation type code: " + code));
    }
}
