package com.smartticket.api.dto.sla;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaPolicyRequest {
    @NotBlank(message = "policyName is required")
    @Size(max = 128, message = "policyName must be <= 128 chars")
    private String policyName;

    private String category;

    private String priority;

    @NotNull(message = "firstResponseMinutes is required")
    @Min(value = 1, message = "firstResponseMinutes must be > 0")
    private Integer firstResponseMinutes;

    @NotNull(message = "resolveMinutes is required")
    @Min(value = 1, message = "resolveMinutes must be > 0")
    private Integer resolveMinutes;

    private Boolean enabled;
}
