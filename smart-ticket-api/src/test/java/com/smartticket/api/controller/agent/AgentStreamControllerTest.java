package com.smartticket.api.controller.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartticket.agent.service.AgentFacade;
import com.smartticket.agent.stream.AgentEventSink;
import com.smartticket.api.advice.ApiExceptionHandler;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.biz.model.CurrentUser;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

/**
 * AgentStreamController SSE 基础测试。
 */
@WebMvcTest(controllers = AgentController.class)
@ContextConfiguration(classes = {
        AgentController.class,
        ApiExceptionHandler.class,
        AgentStreamControllerTest.TestConfig.class
})
class AgentStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentFacade agentFacade;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @Test
    void chatStreamShouldReturnSseEmitter() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(currentUser());

        mockMvc.perform(post("/api/agent/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"test-session\",\"message\":\"hello\"}")
                        .with(authentication(auth())))
                .andExpect(status().isOk());
    }

    @Test
    void chatStreamShouldTriggerAgentFacade() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(currentUser());

        mockMvc.perform(post("/api/agent/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"test-session\",\"message\":\"hello\"}")
                        .with(authentication(auth())));

        verify(agentFacade, timeout(5000)).chatStream(
                any(),
                eq("test-session"),
                eq("hello"),
                any(AgentEventSink.class));
    }

    @Test
    void chatStreamShouldHandleException() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(currentUser());
        doThrow(new RuntimeException("test error")).when(agentFacade).chatStream(
                any(),
                eq("test-session"),
                eq("hello"),
                any(AgentEventSink.class));

        mockMvc.perform(post("/api/agent/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"test-session\",\"message\":\"hello\"}")
                        .with(authentication(auth())))
                .andExpect(status().isOk());
    }

    private static CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }

    private static Authentication auth() {
        return new UsernamePasswordAuthenticationToken(
                "admin1", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean("agentExecutor")
        public Executor agentExecutor() {
            return Executors.newSingleThreadExecutor();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(httpBasic -> httpBasic.disable())
                    .formLogin(form -> form.disable())
                    .build();
        }
    }
}
