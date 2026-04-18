package com.smartticket.biz.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务层使用的当前用户上下文。
 *
 * <p>auth 模块负责认证，api 模块从 Spring Security 中取出当前用户，
 * biz 模块只依赖这个轻量对象做业务权限判断，避免反向依赖 auth。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {
    /** 当前用户 ID。 */
    private Long userId;
    /** 当前用户名。 */
    private String username;
    /** 当前用户拥有的角色编码，例如 USER、STAFF、ADMIN。 */
    private List<String> roles;

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
