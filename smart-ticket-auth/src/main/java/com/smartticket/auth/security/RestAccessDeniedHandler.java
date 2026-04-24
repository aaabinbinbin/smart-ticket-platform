package com.smartticket.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 已认证但权限不足的异常处理器。
 *
 * <p>例如 USER 访问 STAFF 接口时，统一返回 403。</p>
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    /**
     * 处理请求。
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"success":false,"code":"FORBIDDEN","message":"当前用户没有访问权限"}
                """);
    }
}
