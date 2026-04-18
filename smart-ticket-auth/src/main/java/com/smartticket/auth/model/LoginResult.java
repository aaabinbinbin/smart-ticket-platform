package com.smartticket.auth.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功后的返回结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {
    /** JWT 访问令牌。 */
    private String accessToken;
    /** 令牌类型，固定为 Bearer。 */
    private String tokenType;
    /** 过期时间，单位秒。 */
    private long expiresIn;
    /** 用户 ID。 */
    private Long userId;
    /** 用户名。 */
    private String username;
    /** 用户真实姓名。 */
    private String realName;
    /** 用户角色编码，例如 USER、STAFF、ADMIN。 */
    private List<String> roles;
}
