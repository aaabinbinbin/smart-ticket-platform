package com.smartticket.api.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentRuleRequest {
    @NotBlank(message = "ruleName is required")
    @Size(max = 128, message = "ruleName must be <= 128 chars")
    private String ruleName;

    private String category;

    private String priority;

    private Long targetGroupId;

    private Long targetQueueId;

    private Long targetUserId;

    private Integer weight;

    private Boolean enabled;
}
