package com.smartticket.agent.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis backed conversation context reserved for later Agent stages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionContext {
    private Long activeTicketId;
    private Long activeAssigneeId;
    private String lastIntent;
    @Builder.Default
    private List<String> recentMessages = new ArrayList<>();
}
