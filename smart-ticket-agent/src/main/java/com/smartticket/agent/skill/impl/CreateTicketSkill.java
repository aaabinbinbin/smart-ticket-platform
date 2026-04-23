package com.smartticket.agent.skill.impl;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AbstractToolBackedSkill;
import com.smartticket.agent.tool.ticket.CreateTicketTool;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CreateTicketSkill extends AbstractToolBackedSkill {
    public CreateTicketSkill(CreateTicketTool tool) {
        super(tool);
    }

    @Override
    public String skillCode() {
        return "create-ticket";
    }

    @Override
    public List<AgentIntent> supportedIntents() {
        return intent(AgentIntent.CREATE_TICKET);
    }
}
