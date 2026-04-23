package com.smartticket.api.dto.assignment;

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
public class TicketQueueRequest {
    @NotBlank(message = "queueName is required")
    @Size(max = 128, message = "queueName must be <= 128 chars")
    private String queueName;

    @NotBlank(message = "queueCode is required")
    @Size(max = 64, message = "queueCode must be <= 64 chars")
    private String queueCode;

    @NotNull(message = "groupId is required")
    private Long groupId;

    private Boolean enabled;
}
