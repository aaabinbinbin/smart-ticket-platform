package com.smartticket.auth.service;

import com.smartticket.auth.jwt.JwtTokenProvider;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.auth.model.LoginResult;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * 认证服务。
 *
 * <p>只负责登录认证、签发 JWT 和返回基础角色信息，不处理工单业务权限。</p>
 */
@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 使用用户名和密码登录，认证成功后签发 JWT。
     */
    public LoginResult login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(authUser);
        List<String> roles = authUser.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replace("ROLE_", ""))
                .toList();

        LoginResult result = new LoginResult();
        result.setAccessToken(token);
        result.setTokenType("Bearer");
        result.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        result.setUserId(authUser.getUserId());
        result.setUsername(authUser.getUsername());
        result.setRealName(authUser.getRealName());
        result.setRoles(roles);
        return result;
    }
}
