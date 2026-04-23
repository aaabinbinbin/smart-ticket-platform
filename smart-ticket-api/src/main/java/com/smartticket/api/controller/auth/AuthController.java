package com.smartticket.api.controller.auth;

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
 * 认证接口入口。
 *
 * <p>当前只提供用户名密码登录，不包含 OAuth、短信验证码或邮箱验证码。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录。
     *
     * <p>登录成功后返回 JWT 相关信息，后续请求需要在请求头里携带
     * {@code Authorization: Bearer <accessToken>}。</p>
     */
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequestDTO request) {
        return ApiResponse.success(authService.login(request.getUsername(), request.getPassword()));
    }
}
