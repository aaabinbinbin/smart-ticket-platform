package com.smartticket.agent.skill.impl;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AbstractToolBackedSkill;
import com.smartticket.agent.tool.ticket.TransferTicketTool;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TransferTicketSkill extends AbstractToolBackedSkill {
    public TransferTicketSkill(TransferTicketTool tool) {
        super(tool);
    }

    @Override
    public String skillCode() {
        return "transfer-ticket";
    }

    @Override
    public List<AgentIntent> supportedIntents() {
        return intent(AgentIntent.TRANSFER_TICKET);
    }
}
