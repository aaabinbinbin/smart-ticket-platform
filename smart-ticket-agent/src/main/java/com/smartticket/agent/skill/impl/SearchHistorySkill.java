package com.smartticket.agent.skill.impl;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AbstractToolBackedSkill;
import com.smartticket.agent.tool.ticket.SearchHistoryTool;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 搜索历史技能类。
 */
@Component
public class SearchHistorySkill extends AbstractToolBackedSkill {
    /**
     * 构造搜索历史技能。
     */
    public SearchHistorySkill(SearchHistoryTool tool) {
        super(tool);
    }

    /**
     * 处理编码。
     */
    @Override
    public String skillCode() {
        return "search-history";
    }

    /**
     * 处理意图列表。
     */
    @Override
    public List<AgentIntent> supportedIntents() {
        return intent(AgentIntent.SEARCH_HISTORY);
    }
}
