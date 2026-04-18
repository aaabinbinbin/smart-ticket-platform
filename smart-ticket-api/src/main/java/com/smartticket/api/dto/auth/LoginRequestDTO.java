package com.smartticket.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {
    /** 登录用户名。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码。 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
