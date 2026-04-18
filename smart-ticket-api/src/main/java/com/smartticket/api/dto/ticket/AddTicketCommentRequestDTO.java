package com.smartticket.api.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加工单评论请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "添加工单评论请求")
public class AddTicketCommentRequestDTO {
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 5000, message = "评论内容不能超过 5000 个字符")
    @Schema(description = "评论内容", example = "已收到，正在排查登录服务日志")
    private String content;
}
