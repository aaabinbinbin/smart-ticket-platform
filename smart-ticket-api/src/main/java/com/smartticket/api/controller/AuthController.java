package com.smartticket.api.controller;

import com.smartticket.api.dto.auth.LoginRequestDTO;
import com.smartticket.auth.model.LoginResult;
import com.smartticket.auth.service.AuthService;
import com.smartticket.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>当前只提供用户名密码登录，不做 OAuth、短信验证码或邮箱验证码。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录接口。
     *
     * <p>请求成功后返回 JWT。后续访问受保护接口时，需要在请求头中携带：
     * {@code Authorization: Bearer <accessToken>}。</p>
     */
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequestDTO request) {
        return ApiResponse.success(authService.login(request.getUsername(), request.getPassword()));
    }
}
