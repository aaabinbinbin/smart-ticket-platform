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
public class TicketGroupRequest {
    @NotBlank(message = "groupName is required")
    @Size(max = 128, message = "groupName must be <= 128 chars")
    private String groupName;

    @NotBlank(message = "groupCode is required")
    @Size(max = 64, message = "groupCode must be <= 64 chars")
    private String groupCode;

    private Long ownerUserId;

    private Boolean enabled;
}
