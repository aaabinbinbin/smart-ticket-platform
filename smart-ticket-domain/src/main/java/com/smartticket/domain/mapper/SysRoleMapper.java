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

    /**
     * 处理新增。
     */
    int insert(SysRole sysRole);

    /**
     * 查询按ID。
     */
    SysRole findById(@Param("id") Long id);

    /**
     * 查询按角色编码。
     */
    SysRole findByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 查询全部。
     */
    List<SysRole> findAll();
}
