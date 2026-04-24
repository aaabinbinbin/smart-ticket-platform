package com.smartticket.common.exception;

/**
 * 业务异常。
 *
 * <p>用于表达权限不足、状态不允许、资源不存在等可预期业务错误。</p>
 */
public class BusinessException extends RuntimeException {
    /** 业务错误码。 */
    private final String code;

    /**
     * 构造业务异常。
     */
    public BusinessException(BusinessErrorCode errorCode, Object... messageArgs) {
        super(errorCode.formatMessage(messageArgs));
        this.code = errorCode.getCode();
    }

    /**
     * 获取编码。
     */
    public String getCode() {
        return code;
    }
}
