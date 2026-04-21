package com.smartticket.domain.enums;

import java.util.Arrays;

public enum OperationTypeEnum implements CodeInfoEnum {
    CREATE("CREATE", "创建工单"),
    ASSIGN("ASSIGN", "分配处理人"),
    CLAIM("CLAIM", "认领处理"),
    TRANSFER("TRANSFER", "转派处理人"),
    BIND_QUEUE("BIND_QUEUE", "绑定工单队列"),
    AUTO_ASSIGN_MATCHED("AUTO_ASSIGN_MATCHED", "自动分派命中"),
    AUTO_ASSIGN_FALLBACK("AUTO_ASSIGN_FALLBACK", "自动分派回退"),
    AUTO_ASSIGN_PENDING("AUTO_ASSIGN_PENDING", "自动分派待认领"),
    UPDATE_STATUS("UPDATE_STATUS", "更新状态"),
    COMMENT("COMMENT", "添加评论"),
    CLOSE("CLOSE", "关闭工单"),
    SLA_BREACH("SLA_BREACH", "SLA违约"),
    SLA_ESCALATE("SLA_ESCALATE", "SLA升级");

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
