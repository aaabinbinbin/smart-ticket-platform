package com.smartticket.auth.config;

import com.smartticket.auth.jwt.JwtAuthenticationFilter;
import com.smartticket.auth.security.JwtAuthenticationEntryPoint;
import com.smartticket.auth.security.RestAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 基础安全配置。
 *
 * <p>第一版采用无状态 JWT 认证。这里处理的是基础 RBAC：
 * 是否登录、是否具备 USER/STAFF/ADMIN 角色。工单级权限由 biz 模块处理。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    // JWTAuthenticationFilter
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    // authenticationEntryPoint
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    // 访问DeniedHandler
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * 构造安全配置。
     */
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * 处理过滤器链。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF 防护主要针对 Cookie+Session 模式。我们现在用 JWT（放在 Header 里），天然免疫 CSRF，所以关掉它可以减少不必要的开销。
                .csrf(csrf -> csrf.disable())
                // 设置无状态会话，每次请求都自己带 Token 来验证
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // 白名单
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                // 注册 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * DAO 认证提供者，负责用数据库用户和密码完成登录校验。
     */
    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * 密码编码器。
     *
     * <p>支持 {bcrypt}、{noop} 等 Spring Security 标准前缀。seed.sql 为了本地演示使用 {noop}，
     * 生产环境应统一写入 BCrypt 哈希。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 处理负责人。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
