package com.smartticket.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.service.AgentFacade;
import com.smartticket.api.controller.agent.AgentController;
import com.smartticket.api.dto.agent.AgentChatRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.SysUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentFacade agentFacade;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Test
    void chatShouldRejectNullAuthentication() {
        AgentController controller = new AgentController(agentFacade, currentUserResolver);
        when(currentUserResolver.resolve(null)).thenThrow(new BusinessException(BusinessErrorCode.UNAUTHORIZED));

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.chat(null, request()));

        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void chatShouldRejectUnexpectedPrincipalType() {
        AgentController controller = new AgentController(agentFacade, currentUserResolver);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "plain-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(currentUserResolver.resolve(authentication)).thenThrow(new BusinessException(BusinessErrorCode.UNAUTHORIZED));

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.chat(authentication, request()));

        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void chatShouldMapAuthUserToCurrentUser() {
        AgentController controller = new AgentController(agentFacade, currentUserResolver);
        AuthUser authUser = new AuthUser(
                SysUser.builder().id(1L).username("user1").passwordHash("{noop}123456").realName("User One").status(1).build(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_STAFF"))
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                authUser,
                null,
                authUser.getAuthorities()
        );
        CurrentUser currentUser = CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER", "STAFF"))
                .build();
        when(currentUserResolver.resolve(authentication)).thenReturn(currentUser);
        when(agentFacade.chat(any(), eq("session-1"), eq("查询工单"))).thenReturn(AgentChatResult.builder()
                .sessionId("session-1")
                .intent("QUERY_TICKET")
                .reply("ok")
                .route(IntentRoute.builder().confidence(0.9d).reason("test").build())
                .build());

        controller.chat(authentication, request());

        verify(currentUserResolver).resolve(authentication);
        verify(agentFacade).chat(eq(currentUser), eq("session-1"), eq("查询工单"));
    }

    private AgentChatRequest request() {
        return AgentChatRequest.builder()
                .sessionId("session-1")
                .message("查询工单")
                .build();
    }
}
