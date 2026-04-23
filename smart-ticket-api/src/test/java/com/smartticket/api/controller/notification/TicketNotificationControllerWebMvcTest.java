package com.smartticket.api.controller.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartticket.api.advice.ApiExceptionHandler;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.notification.TicketNotificationService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.TicketNotification;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TicketNotificationController.class)
@ContextConfiguration(classes = {
        TicketNotificationController.class,
        ApiExceptionHandler.class,
        TicketNotificationControllerWebMvcTest.TestSecurityConfig.class
})
class TicketNotificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TicketNotificationService ticketNotificationService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @Test
    void pageShouldReturnCurrentUserNotifications() throws Exception {
        mockCurrentUser();
        when(ticketNotificationService.pageMyNotifications(any(), any())).thenReturn(PageResult.<TicketNotification>builder()
                .pageNo(1)
                .pageSize(10)
                .total(1)
                .records(List.of(TicketNotification.builder()
                        .id(501L)
                        .ticketId(1001L)
                        .receiverUserId(1L)
                        .channel("IN_APP")
                        .notificationType("SLA_BREACH")
                        .title("工单 INC202604230001 首次响应超时")
                        .content("工单发生 SLA 违约")
                        .readStatus(0)
                        .createdAt(LocalDateTime.of(2026, 4, 23, 14, 30))
                        .build()))
                .build());

        mockMvc.perform(get("/api/notifications")
                        .with(authentication(auth()))
                        .param("pageNo", "1")
                        .param("pageSize", "10")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(501))
                .andExpect(jsonPath("$.data.records[0].notificationType").value("SLA_BREACH"))
                .andExpect(jsonPath("$.data.records[0].read").value(false));
    }

    @Test
    void markReadShouldReturnUpdatedNotification() throws Exception {
        mockCurrentUser();
        when(ticketNotificationService.markRead(any(), any())).thenReturn(TicketNotification.builder()
                .id(502L)
                .ticketId(1002L)
                .receiverUserId(1L)
                .channel("IN_APP")
                .notificationType("SLA_BREACH")
                .title("工单 INC202604230002 解决时限超时")
                .content("系统已执行升级处理")
                .readStatus(1)
                .readAt(LocalDateTime.of(2026, 4, 23, 14, 35))
                .createdAt(LocalDateTime.of(2026, 4, 23, 14, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 23, 14, 35))
                .build());

        mockMvc.perform(patch("/api/notifications/502/read")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(502))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void pageShouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void mockCurrentUser() {
        when(currentUserResolver.resolve(any())).thenReturn(CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build());
    }

    private Authentication auth() {
        AuthUser authUser = new AuthUser(
                SysUser.builder().id(1L).username("user1").passwordHash("{noop}123456").realName("User One").status(1).build(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(401);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"请先登录或提供有效令牌\"}");
                    }))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(httpBasic -> httpBasic.disable())
                    .formLogin(form -> form.disable())
                    .build();
        }
    }
}
