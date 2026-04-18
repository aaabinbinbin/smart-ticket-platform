package com.smartticket.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP 接口统一响应结构。
 *
 * <p>当前只提供基础可用版本，后续可以继续补充分页响应、错误码枚举和链路追踪 ID。</p>
 *
 * @param <T> 响应数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    /** 是否成功。 */
    private boolean success;
    /** 业务响应码。 */
    private String code;
    /** 响应消息。 */
    private String message;
    /** 响应数据。 */
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setCode("SUCCESS");
        response.setMessage("操作成功");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
