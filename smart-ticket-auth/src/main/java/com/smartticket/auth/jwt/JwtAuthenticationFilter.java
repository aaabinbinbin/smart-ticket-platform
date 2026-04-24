package com.smartticket.auth.jwt;

import com.smartticket.auth.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器。
 *
 * <p>从 Authorization 请求头中读取 Bearer Token，校验通过后写入 Spring Security 上下文。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // HEADER
    private static final String AUTHORIZATION_HEADER = "Authorization";
    // PREFIX
    private static final String BEARER_PREFIX = "Bearer ";

    // JWT令牌提供器
    private final JwtTokenProvider jwtTokenProvider;
    // 用户Details服务
    private final CustomUserDetailsService userDetailsService;

    /**
     * 构造JWTAuthenticationFilter。
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CustomUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * 执行FilterInternal。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 1. 从请求头拿 Token
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 2. 验证 Token 是否合法（签名、过期）
                jwtTokenProvider.validateToken(token);
                // 3. 从 Token 里拿出用户名
                String username = jwtTokenProvider.getUsername(token);
                // 4. 根据用户名查数据库，获取最新权限
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                // 5. 把用户信息存入 Spring Security 上下文
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ex) {
                // 令牌无效时清空上下文，后续由认证入口统一返回 401。
                SecurityContextHolder.clearContext();
            }
        }
        // 6. 放行，去执行 Controller
        filterChain.doFilter(request, response);
    }

    /**
     * 解析令牌。
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
