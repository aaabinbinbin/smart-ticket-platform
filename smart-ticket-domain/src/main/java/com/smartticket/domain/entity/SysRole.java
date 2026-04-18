package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统角色实体，对应表 {@code sys_role}。
 *
 * <p>第一版角色固定为 USER、STAFF、ADMIN。角色表示系统能力，
 * 不表示提单人、处理人这类工单内业务位置。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysRole {
    /** 角色主键。 */
    private Long id;
    /** 角色编码，例如 USER、STAFF、ADMIN。 */
    private String roleCode;
    /** 角色名称。 */
    private String roleName;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
