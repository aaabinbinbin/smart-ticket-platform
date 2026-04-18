package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.SysRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统角色 Mapper，对应表 {@code sys_role}。
 */
@Mapper
public interface SysRoleMapper {

    int insert(SysRole sysRole);

    SysRole findById(@Param("id") Long id);

    SysRole findByRoleCode(@Param("roleCode") String roleCode);

    List<SysRole> findAll();
}
