package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统用户实体，对应表 {@code sys_user}。
 *
 * <p>该实体只表达用户基础身份信息。用户在某张工单中是提单人还是处理人，
 * 由 {@code ticket.creator_id} 和 {@code ticket.assignee_id} 这类业务关系字段决定。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUser {
    /** 用户主键。 */
    private Long id;
    /** 登录用户名，全局唯一。 */
    private String username;
    /** 密码哈希值，不保存明文密码。 */
    private String passwordHash;
    /** 用户真实姓名或展示名。 */
    private String realName;
    /** 用户邮箱。 */
    private String email;
    /** 账号状态：1-启用，0-禁用。 */
    private Integer status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
