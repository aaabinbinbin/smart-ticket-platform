package com.smartticket.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.service.AgentFacade;
import com.smartticket.api.advice.ApiExceptionHandler;
import com.smartticket.api.controller.agent.AgentController;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.entity.SysUser;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

@WebMvcTest(controllers = AgentController.class)
@ContextConfiguration(classes = {
        AgentController.class,
        ApiExceptionHandler.class,
        AgentControllerWebMvcTest.TestSecurityConfig.class
})
class AgentControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentFacade agentFacade;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @Test
    void chatShouldHandleQueryTicketIntent() throws Exception {
        mockCurrentUser();
        mockIntent(AgentIntent.QUERY_TICKET, "已查询工单详情。", 1001L);

        mockMvc.perform(post("/api/agent/chat")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s-query\",\"message\":\"查一下1001号工单\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent").value("QUERY_TICKET"))
                .andExpect(jsonPath("$.data.reply").value("已查询工单详情。"))
                .andExpect(jsonPath("$.data.context.activeTicketId").value(1001));
    }

    @Test
    void chatShouldHandleCreateTicketIntent() throws Exception {
        mockCurrentUser();
        mockIntent(AgentIntent.CREATE_TICKET, "已创建工单。", 2001L);

        mockMvc.perform(post("/api/agent/chat")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s-create\",\"message\":\"帮我创建一个登录故障工单\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("CREATE_TICKET"));
    }

    @Test
    void chatShouldHandleTransferTicketIntentWithInheritedTicketContext() throws Exception {
        mockCurrentUser();
        mockIntent(AgentIntent.TRANSFER_TICKET, "已转派工单。", 3001L);

        mockMvc.perform(post("/api/agent/chat")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s-transfer\",\"message\":\"把刚才那个工单转给3号处理人\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("TRANSFER_TICKET"))
                .andExpect(jsonPath("$.data.context.activeTicketId").value(3001));
    }

    @Test
    void chatShouldHandleSearchHistoryIntent() throws Exception {
        mockCurrentUser();
        mockIntent(AgentIntent.SEARCH_HISTORY, "已检索到相似历史经验。", null);

        mockMvc.perform(post("/api/agent/chat")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s-history\",\"message\":\"查一下历史上类似的登录报错\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("SEARCH_HISTORY"));
    }

    @Test
    void chatShouldRejectInvalidInput() throws Exception {
        mockCurrentUser();
        mockMvc.perform(post("/api/agent/chat")
                        .with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s-invalid\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void chatShouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s-unauth\",\"message\":\"查询工单\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void mockIntent(AgentIntent intent, String reply, Long activeTicketId) {
        when(agentFacade.chat(any(), any(), any())).thenReturn(AgentChatResult.builder()
                .sessionId("session")
                .intent(intent.name())
                .reply(reply)
                .route(IntentRoute.builder()
                        .intent(intent)
                        .confidence(0.92d)
                        .reason("mock")
                        .build())
                .context(AgentSessionContext.builder().activeTicketId(activeTicketId).build())
                .springAiChatReady(false)
                .build());
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
