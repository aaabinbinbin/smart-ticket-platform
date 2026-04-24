package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Sys用户映射接口定义。
 */
@Mapper
public interface SysUserMapper {

    /**
     * 处理新增。
     */
    int insert(SysUser sysUser);

    /**
     * 查询按ID。
     */
    SysUser findById(@Param("id") Long id);

    /**
     * 按用户名查询。
     */
    SysUser findByUsername(@Param("username") String username);

    /**
     * 查询全部。
     */
    List<SysUser> findAll();

    /**
     * 查询按角色编码。
     */
    List<SysUser> findByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 更新BasicInfo。
     */
    int updateBasicInfo(SysUser sysUser);
}
