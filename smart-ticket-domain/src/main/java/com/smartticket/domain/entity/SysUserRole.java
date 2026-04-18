package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户角色关联实体，对应表 {@code sys_user_role}。
 *
 * <p>一个用户可以绑定多个角色，例如 STAFF 通常同时具备 USER 能力。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUserRole {
    /** 关联主键。 */
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 角色 ID。 */
    private Long roleId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
