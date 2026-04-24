package com.smartticket.auth.model;

import com.smartticket.domain.entity.SysUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security 使用的当前登录用户对象。
 *
 * <p>该对象只保存认证和基础角色信息，不保存某张工单上的业务权限判断结果。</p>
 */
public class AuthUser implements UserDetails {
    /** 用户 ID。 */
    private final Long userId;
    /** 登录用户名。 */
    private final String username;
    /** 密码哈希。 */
    private final String password;
    /** 用户真实姓名。 */
    private final String realName;
    /** 账号是否启用。 */
    private final boolean enabled;
    /** Spring Security 权限集合，例如 ROLE_USER、ROLE_STAFF、ROLE_ADMIN。 */
    private final List<GrantedAuthority> authorities;

    /**
     * 构造认证用户。
     */
    public AuthUser(SysUser sysUser, List<GrantedAuthority> authorities) {
        this.userId = sysUser.getId();
        this.username = sysUser.getUsername();
        this.password = sysUser.getPasswordHash();
        this.realName = sysUser.getRealName();
        this.enabled = Integer.valueOf(1).equals(sysUser.getStatus());
        this.authorities = authorities;
    }

    /**
     * 获取用户ID。
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取RealName。
     */
    public String getRealName() {
        return realName;
    }

    /**
     * 获取Authorities。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 获取Password。
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * 获取Username。
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 判断账号是否未过期。
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 判断账号是否未锁定。
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 判断凭证是否未过期。
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 处理启用。
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
