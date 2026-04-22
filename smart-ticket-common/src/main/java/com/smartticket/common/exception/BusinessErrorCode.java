package com.smartticket.common.exception;

public enum BusinessErrorCode {
    UNAUTHORIZED("UNAUTHORIZED", "���ȵ�¼���ṩ��Чƾ֤"),
    INVALID_TICKET_STATUS("INVALID_TICKET_STATUS", "����״̬���Ϸ�: %s"),
    INVALID_TICKET_TYPE("INVALID_TICKET_TYPE", "��֧�ֵĹ�������: %s"),
    INVALID_TICKET_SUMMARY_VIEW("INVALID_TICKET_SUMMARY_VIEW", "��֧�ֵĹ�����ժҪ�ӽ�: %s"),
    INVALID_TICKET_TYPE_REQUIREMENT("INVALID_TICKET_TYPE_REQUIREMENT", "��������У��δͨ��: %s"),
    INVALID_TICKET_CATEGORY("INVALID_TICKET_CATEGORY", "��֧�ֵĹ�������: %s"),
    INVALID_TICKET_PRIORITY("INVALID_TICKET_PRIORITY", "��֧�ֵĹ������ȼ�: %s"),
    TICKET_FORBIDDEN("TICKET_FORBIDDEN", "��ǰ�û���Ȩ�鿴�ù���"),
    ADMIN_REQUIRED("ADMIN_REQUIRED", "�ò�����Ҫ ADMIN ��ɫ"),
    TICKET_TRANSFER_FORBIDDEN("TICKET_TRANSFER_FORBIDDEN", "ֻ�е�ǰ�����˻����Ա����ת�ɹ���"),
    TICKET_RESOLVE_FORBIDDEN("TICKET_RESOLVE_FORBIDDEN", "ֻ�е�ǰ�����˻����Ա���Խ������"),
    TICKET_CLOSE_FORBIDDEN("TICKET_CLOSE_FORBIDDEN", "ֻ���ᵥ�˻����Ա���Թرչ���"),
    TICKET_CLAIM_FORBIDDEN("TICKET_CLAIM_FORBIDDEN", "��ǰ�û���Ȩ����ù���"),
    TICKET_APPROVAL_FORBIDDEN("TICKET_APPROVAL_FORBIDDEN", "��ǰ�û���Ȩ����ù�������: %s"),
    INVALID_TICKET_CLAIM("INVALID_TICKET_CLAIM", "������������������: %s"),
    INVALID_TICKET_APPROVAL("INVALID_TICKET_APPROVAL", "������������������: %s"),
    TICKET_APPROVAL_REQUIRED("TICKET_APPROVAL_REQUIRED", "������Ҫ���������: %s"),
    TICKET_STATUS_REQUIRED("TICKET_STATUS_REQUIRED", "Ŀ��״̬����Ϊ��"),
    TICKET_CLOSED("TICKET_CLOSED", "�ѹرչ������ܼ�������"),
    TICKET_STATUS_UNCHANGED("TICKET_STATUS_UNCHANGED", "�����Ѿ�����Ŀ��״̬"),
    TICKET_ASSIGNEE_REQUIRED("TICKET_ASSIGNEE_REQUIRED", "���봦����ǰ�����ȷ��䴦����"),
    TICKET_STATE_CHANGED("TICKET_STATE_CHANGED", "����״̬�ѱ仯����ˢ�º�����"),
    CLOSE_TICKET_USE_CLOSE_API("CLOSE_TICKET_USE_CLOSE_API", "�رչ�����ʹ�ùرսӿ�"),
    INVALID_TICKET_STATUS_TRANSITION(
            "INVALID_TICKET_STATUS_TRANSITION",
            "״̬��ת���Ϸ���ֻ���� PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED"
    ),
    TICKET_NOT_FOUND("TICKET_NOT_FOUND", "����������"),
    TICKET_APPROVAL_NOT_FOUND("TICKET_APPROVAL_NOT_FOUND", "����������¼������"),
    TICKET_APPROVAL_TEMPLATE_NOT_FOUND("TICKET_APPROVAL_TEMPLATE_NOT_FOUND", "����ģ�岻����"),
    ASSIGNEE_NOT_FOUND("ASSIGNEE_NOT_FOUND", "Ŀ�괦���˲����ڻ��ѽ���"),
    ASSIGNEE_NOT_STAFF("ASSIGNEE_NOT_STAFF", "Ŀ�괦���˱���߱� STAFF ��ɫ"),
    TICKET_GROUP_NOT_FOUND("TICKET_GROUP_NOT_FOUND", "�����鲻����"),
    TICKET_GROUP_CODE_DUPLICATED("TICKET_GROUP_CODE_DUPLICATED", "����������Ѵ���: %s"),
    TICKET_GROUP_DISABLED("TICKET_GROUP_DISABLED", "��������ͣ��"),
    TICKET_QUEUE_NOT_FOUND("TICKET_QUEUE_NOT_FOUND", "�������в�����"),
    TICKET_QUEUE_CODE_DUPLICATED("TICKET_QUEUE_CODE_DUPLICATED", "�������б����Ѵ���: %s"),
    TICKET_SLA_POLICY_NOT_FOUND("TICKET_SLA_POLICY_NOT_FOUND", "SLA ���Բ�����"),
    TICKET_SLA_INSTANCE_NOT_FOUND("TICKET_SLA_INSTANCE_NOT_FOUND", "���� SLA ʵ��������"),
    INVALID_TICKET_SLA_POLICY("INVALID_TICKET_SLA_POLICY", "SLA �������ò��Ϸ�: %s"),
    TICKET_ASSIGNMENT_RULE_NOT_FOUND("TICKET_ASSIGNMENT_RULE_NOT_FOUND", "�Զ����ɹ��򲻴���"),
    TICKET_ASSIGNMENT_RULE_NOT_MATCHED("TICKET_ASSIGNMENT_RULE_NOT_MATCHED", "δƥ�䵽���õ��Զ����ɹ���"),
    INVALID_TICKET_ASSIGNMENT_RULE("INVALID_TICKET_ASSIGNMENT_RULE", "�Զ����ɹ������ò��Ϸ�: %s"),
    INVALID_IDEMPOTENCY_KEY("INVALID_IDEMPOTENCY_KEY", "�ݵȼ����Ϸ������Ȳ��ܳ��� 128 ���ַ��Ҳ��ܰ��������ַ�"),
    IDEMPOTENT_REQUEST_PROCESSING("IDEMPOTENT_REQUEST_PROCESSING", "��ͬ�ݵȼ��Ĵ����������ڴ����У����Ժ�����");

    private final String code;
    private final String message;

    BusinessErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }
}
