package com.smartticket.api.controller.admin;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartticket.api.advice.ApiExceptionHandler;
import com.smartticket.biz.service.dashboard.DashboardService;
import com.smartticket.biz.service.dashboard.DashboardService.AgentMetrics;
import com.smartticket.biz.service.dashboard.DashboardService.RagMetrics;
import com.smartticket.biz.service.dashboard.DashboardService.TicketMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AdminDashboardController 权限和指标测试。
 */
@WebMvcTest(controllers = AdminDashboardController.class)
@ContextConfiguration(classes = {
        AdminDashboardController.class,
        ApiExceptionHandler.class,
        AdminDashboardControllerTest.TestSecurityConfig.class
})
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    void adminShouldAccessDashboard() throws Exception {
        TicketMetrics ticketMetrics = new TicketMetrics();
        ticketMetrics.pendingAssignCount = 1;
        ticketMetrics.processingCount = 2;
        ticketMetrics.resolvedCount = 3;
        ticketMetrics.closedCount = 4;
        ticketMetrics.todayCreatedCount = 5;

        RagMetrics ragMetrics = new RagMetrics();
        ragMetrics.knowledgeCount = 10;
        ragMetrics.knowledgeBuildSuccessCount = 8;
        ragMetrics.knowledgeBuildFailedCount = 1;
        ragMetrics.embeddingChunkCount = 80;
        ragMetrics.retrievalPath = "PGVECTOR";

        AgentMetrics agentMetrics = new AgentMetrics();
        agentMetrics.recentAgentCallCount = 20;
        agentMetrics.recentSuccessCount = 18;
        agentMetrics.avgLatencyMs = 120.0;

        when(dashboardService.aggregateTicket()).thenReturn(ticketMetrics);
        when(dashboardService.aggregateRag()).thenReturn(ragMetrics);
        when(dashboardService.aggregateAgent()).thenReturn(agentMetrics);

        mockMvc.perform(get("/api/admin/dashboard")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticket.pendingAssignCount").value(1))
                .andExpect(jsonPath("$.data.ticket.processingCount").value(2))
                .andExpect(jsonPath("$.data.rag.knowledgeCount").value(10))
                .andExpect(jsonPath("$.data.rag.retrievalPath").value("PGVECTOR"))
                .andExpect(jsonPath("$.data.agent.recentAgentCallCount").value(20));
    }

    @Test
    void userShouldNotAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .with(authentication(userAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardShouldReturnTicketRagAgentSections() throws Exception {
        when(dashboardService.aggregateTicket()).thenReturn(new TicketMetrics());
        when(dashboardService.aggregateRag()).thenReturn(new RagMetrics());
        when(dashboardService.aggregateAgent()).thenReturn(new AgentMetrics());

        mockMvc.perform(get("/api/admin/dashboard")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").exists())
                .andExpect(jsonPath("$.data.rag").exists())
                .andExpect(jsonPath("$.data.agent").exists());
    }

    @Test
    void unauthenticatedRequestShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    private static Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin1", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static Authentication userAuth() {
        return new UsernamePasswordAuthenticationToken(
                "user1", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @TestConfiguration
    @EnableMethodSecurity
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
