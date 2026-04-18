package com.smartticket.api.controller;

import com.smartticket.auth.model.AuthUser;
import com.smartticket.common.response.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础 RBAC 受保护接口示例。
 *
 * <p>这些接口只演示系统角色控制。某张工单能不能关闭、转派、评论等业务权限，
 * 后续应放到 biz 模块结合工单关系和状态判断。</p>
 */
@RestController
@RequestMapping("/api/examples/security")
public class SecurityExampleController {

    /**
     * 任意已登录用户都可以访问。
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser(Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", authUser.getUserId());
        data.put("username", authUser.getUsername());
        data.put("realName", authUser.getRealName());
        data.put("roles", authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replace("ROLE_", ""))
                .toList());
        return ApiResponse.success(data);
    }

    /**
     * USER 角色可访问。普通提单用户、处理人员、管理员都可以拥有 USER。
     */
    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<String> userOnly() {
        return ApiResponse.success("当前用户具备 USER 角色");
    }

    /**
     * STAFF 角色可访问。用于演示处理人员入口。
     */
    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<String> staffOnly() {
        return ApiResponse.success("当前用户具备 STAFF 角色");
    }

    /**
     * ADMIN 角色可访问。用于演示管理员入口。
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> adminOnly() {
        return ApiResponse.success("当前用户具备 ADMIN 角色");
    }
}
