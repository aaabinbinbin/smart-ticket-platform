package com.smartticket.api.dto.assignment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMemberRequest {
    @NotNull(message = "鎴愬憳鐢ㄦ埛涓嶈兘涓虹┖")
    private Long userId;

    private Boolean enabled;
}
