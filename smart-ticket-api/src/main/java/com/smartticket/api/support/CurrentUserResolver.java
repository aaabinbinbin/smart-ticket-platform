package com.smartticket.api.support;

import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 认证用户解析器。
 *
 * <p>统一把 Spring Security 的认证对象转换成业务层使用的 {@link CurrentUser}，
 * 避免每个 Controller 重复写一遍用户提取逻辑。</p>
 */
@Component
public class CurrentUserResolver {
    public CurrentUser resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }
        return CurrentUser.builder()
                .userId(authUser.getUserId())
                .username(authUser.getUsername())
                .roles(authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(authority -> authority.replace("ROLE_", ""))
                        .toList())
                .build();
    }
}
