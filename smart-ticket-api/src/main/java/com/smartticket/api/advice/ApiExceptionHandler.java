package com.smartticket.api.advice;

import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * HTTP 接口全局异常处理。
 *
 * <p>这里处理 Controller 层常见异常。认证过滤链中的 401/403 由 auth 模块的安全处理器负责。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 业务异常，例如状态流转不合法、资源不存在、业务权限不足。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = "UNAUTHORIZED".equals(ex.getCode()) ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
    }

    /**
     * 登录密码错误或用户名不存在。
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleBadCredentials() {
        return ApiResponse.failure("BAD_CREDENTIALS", "用户名或密码错误");
    }

    /**
     * 账号被禁用。
     */
    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleDisabled() {
        return ApiResponse.failure("ACCOUNT_DISABLED", "账号已被禁用");
    }

    /**
     * JSON 请求体参数校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数校验失败" : error.getDefaultMessage())
                .orElse("参数校验失败");
        return ApiResponse.failure("VALIDATION_FAILED", message);
    }

    /**
     * 表单或查询参数绑定失败。
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数绑定失败" : error.getDefaultMessage())
                .orElse("参数绑定失败");
        return ApiResponse.failure("BIND_FAILED", message);
    }

    /**
     * 路径参数或查询参数校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
                .stream()
                .findFirst()
                .map(violation -> violation.getMessage() == null ? "参数校验失败" : violation.getMessage())
                .orElse("参数校验失败");
        return ApiResponse.failure("VALIDATION_FAILED", message);
    }

    /**
     * 缺少必填请求头（如 Idempotency-Key）。
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        return ApiResponse.failure("MISSING_HEADER", "缺少必填请求头: " + ex.getHeaderName());
    }

    /**
     * 缺少必填请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingRequestParam(MissingServletRequestParameterException ex) {
        return ApiResponse.failure("MISSING_PARAM", "缺少必填请求参数: " + ex.getParameterName());
    }

    /**
     * 请求参数类型不匹配。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch() {
        return ApiResponse.failure("TYPE_MISMATCH", "请求参数类型不匹配");
    }

    /**
     * 请求体格式错误。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMalformedJson() {
        return ApiResponse.failure("MALFORMED_JSON", "请求体格式错误，请检查 JSON 结构");
    }

    /**
     * 数据访问异常（Redis 不可用等）的降级处理。
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleDataAccess(DataAccessException ex) {
        log.error("数据访问异常: {}", ex.getMessage());
        return ApiResponse.failure("SERVICE_DEGRADED", "服务暂时降级，请稍后重试");
    }

    /**
     * 兜底：所有未显式处理的异常返回 500。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception ex) {
        log.error("未预期的异常: {}", ex.getMessage(), ex);
        return ApiResponse.failure("INTERNAL_ERROR", "服务器内部错误");
    }
}
