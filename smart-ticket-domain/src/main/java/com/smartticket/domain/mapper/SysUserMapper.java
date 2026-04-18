package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * System user mapper for table {@code sys_user}.
 */
@Mapper
public interface SysUserMapper {

    int insert(SysUser sysUser);

    SysUser findById(@Param("id") Long id);

    SysUser findByUsername(@Param("username") String username);

    List<SysUser> findAll();

    int updateBasicInfo(SysUser sysUser);
}
