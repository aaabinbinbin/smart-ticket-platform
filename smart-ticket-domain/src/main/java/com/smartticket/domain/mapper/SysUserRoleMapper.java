package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUserRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户角色关系 Mapper，对应表 {@code sys_user_role}。
 */
@Mapper
public interface SysUserRoleMapper {

    /**
     * 处理新增。
     */
    int insert(SysUserRole sysUserRole);

    /**
     * 查询按ID。
     */
    SysUserRole findById(@Param("id") Long id);

    /**
     * 查询按用户ID。
     */
    List<SysUserRole> findByUserId(@Param("userId") Long userId);

    /**
     * 查询Roles按用户ID。
     */
    List<SysRole> findRolesByUserId(@Param("userId") Long userId);
}
