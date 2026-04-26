package com.smartticket.api.controller.ticket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartticket.api.advice.ApiExceptionHandler;
import com.smartticket.api.assembler.ticket.TicketAssembler;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.support.TicketRequestParser;
import com.smartticket.api.vo.ticket.TicketVO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.ticket.TicketService;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketStatusEnum;
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

/**
 * 工单创建幂等键 Web 层测试。
 */
@WebMvcTest(controllers = TicketController.class)
@ContextConfiguration(classes = {
        TicketController.class,
        ApiExceptionHandler.class,
        TicketControllerIdempotencyKeyTest.TestSecurityConfig.class
})
class TicketControllerIdempotencyKeyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private TicketRequestParser ticketRequestParser;

    @MockBean
    private TicketAssembler ticketAssembler;

    private final String validBody = """
            {
                "title": "测试环境无法登录",
                "description": "登录时报 500，影响研发自测"
            }
            """;

    @Test
    void createTicketShouldSucceedWithValidIdempotencyKey() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(new CurrentUser(1L, "user1", List.of("USER")));
        when(ticketRequestParser.parseType(null)).thenReturn(null);
        when(ticketRequestParser.parseCategory(null)).thenReturn(null);
        when(ticketRequestParser.parsePriority(null)).thenReturn(null);
        Ticket created = Ticket.builder().id(101L).title("测试环境无法登录").status(TicketStatusEnum.PENDING_ASSIGN).build();
        when(ticketService.createTicket(any(), any())).thenReturn(created);
        when(ticketAssembler.toVO(created)).thenReturn(TicketVO.builder().id(101L).build());

        mockMvc.perform(post("/api/tickets")
                        .header("Idempotency-Key", "create-ticket-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody)
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createTicketShouldReturn400WhenIdempotencyKeyHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody)
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTicketShouldReturn400WhenIdempotencyKeyEmpty() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(new CurrentUser(1L, "user1", List.of("USER")));

        mockMvc.perform(post("/api/tickets")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody)
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest());
    }

    private static Authentication auth() {
        return new UsernamePasswordAuthenticationToken(
                "user1", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));
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
