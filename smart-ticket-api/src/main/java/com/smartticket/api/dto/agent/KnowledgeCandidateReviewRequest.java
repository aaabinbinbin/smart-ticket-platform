package com.smartticket.api.dto.agent;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识候选审核请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCandidateReviewRequest {
    // 评论
    @Size(max = 1000, message = "comment length must be <= 1000")
    private String comment;
}
