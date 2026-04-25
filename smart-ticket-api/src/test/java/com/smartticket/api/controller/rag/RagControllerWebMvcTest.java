package com.smartticket.api.controller.rag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartticket.api.advice.ApiExceptionHandler;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.rag.model.RetrievalHit;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.service.RetrievalService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RagController Web 层测试。
 */
@WebMvcTest(controllers = RagController.class)
@ContextConfiguration(classes = {
        RagController.class,
        ApiExceptionHandler.class,
        RagControllerWebMvcTest.TestSecurityConfig.class
})
class RagControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RetrievalService retrievalService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    /**
     * 测试 RAG 检索接口返回 PGVECTOR 路径。
     */
    @Test
    void searchShouldReturnPgvectorResult() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(new CurrentUser(1L, "test-user", List.of("ROLE_ADMIN")));

        RetrievalResult mockResult = RetrievalResult.builder()
                .queryText("测试环境登录 token 过期")
                .rewrittenQuery("测试环境登录 token 过期")
                .topK(5)
                .retrievalPath("PGVECTOR")
                .fallbackUsed(false)
                .hits(List.of(
                        RetrievalHit.builder()
                                .knowledgeId(218L)
                                .ticketId(10L)
                                .score(0.83)
                                .chunkText("auth-service 缓存了旧的 signing-key，导致 token 验证失败。")
                                .whyMatched("命中处理步骤摘要，可复用为解决方案参考。")
                                .build()
                ))
                .build();

        when(retrievalService.retrieve(any())).thenReturn(mockResult);

        mockMvc.perform(get("/api/rag/search")
                        .param("query", "测试环境登录 token 过期")
                        .param("topK", "5")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.retrievalPath").value("PGVECTOR"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(false))
                .andExpect(jsonPath("$.data.queryText").value("测试环境登录 token 过期"))
                .andExpect(jsonPath("$.data.topK").value(5))
                .andExpect(jsonPath("$.data.hits").isArray())
                .andExpect(jsonPath("$.data.hits[0].knowledgeId").value(218))
                .andExpect(jsonPath("$.data.hits[0].ticketId").value(10))
                .andExpect(jsonPath("$.data.hits[0].score").value(0.83))
                .andExpect(jsonPath("$.data.hits[0].whyMatched").isString());
    }

    /**
     * 测试 RAG 检索接口默认 topK 为 5。
     */
    @Test
    void searchShouldUseDefaultTopK() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(new CurrentUser(1L, "test-user", List.of("ROLE_ADMIN")));
        when(retrievalService.retrieve(any())).thenReturn(RetrievalResult.builder()
                .queryText("test")
                .topK(5)
                .retrievalPath("MYSQL_FALLBACK")
                .fallbackUsed(true)
                .hits(List.of())
                .build());

        mockMvc.perform(get("/api/rag/search")
                        .param("query", "test")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 测试 RAG 检索接口未认证时返回 401。
     */
    @Test
    void searchShouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/rag/search")
                        .param("query", "test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static Authentication auth() {
        return new UsernamePasswordAuthenticationToken(
                "admin1", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
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
