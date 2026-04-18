package com.smartticket.auth.service;

import com.smartticket.auth.model.AuthUser;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.mapper.SysUserMapper;
import com.smartticket.domain.mapper.SysUserRoleMapper;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 用户加载服务。
 *
 * <p>根据用户名加载用户基础信息和 USER/STAFF/ADMIN 角色。工单关系权限不在这里判断。</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private static final String ROLE_PREFIX = "ROLE_";

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public CustomUserDetailsService(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser sysUser = sysUserMapper.findByUsername(username);
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<GrantedAuthority> authorities = sysUserRoleMapper.findRolesByUserId(sysUser.getId())
                .stream()
                .map(SysRole::getRoleCode)
                .map(roleCode -> ROLE_PREFIX + roleCode)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        return new AuthUser(sysUser, authorities);
    }
}
