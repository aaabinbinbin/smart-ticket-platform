package com.smartticket.agent.skill.impl;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AbstractToolBackedSkill;
import com.smartticket.agent.tool.ticket.SearchHistoryTool;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SearchHistorySkill extends AbstractToolBackedSkill {
    public SearchHistorySkill(SearchHistoryTool tool) {
        super(tool);
    }

    @Override
    public String skillCode() {
        return "search-history";
    }

    @Override
    public List<AgentIntent> supportedIntents() {
        return intent(AgentIntent.SEARCH_HISTORY);
    }
}
