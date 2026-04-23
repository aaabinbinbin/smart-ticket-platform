package com.smartticket.agent.skill.impl;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AbstractToolBackedSkill;
import com.smartticket.agent.tool.ticket.QueryTicketTool;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QueryTicketSkill extends AbstractToolBackedSkill {
    public QueryTicketSkill(QueryTicketTool tool) {
        super(tool);
    }

    @Override
    public String skillCode() {
        return "query-ticket";
    }

    @Override
    public List<AgentIntent> supportedIntents() {
        return intent(AgentIntent.QUERY_TICKET);
    }
}
