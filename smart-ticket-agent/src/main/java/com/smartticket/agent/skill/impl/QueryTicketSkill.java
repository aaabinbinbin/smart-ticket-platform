package com.smartticket.agent.skill.impl;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AbstractToolBackedSkill;
import com.smartticket.agent.tool.ticket.QueryTicketTool;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询工单技能类。
 */
@Component
public class QueryTicketSkill extends AbstractToolBackedSkill {
    /**
     * 构造查询工单技能。
     */
    public QueryTicketSkill(QueryTicketTool tool) {
        super(tool);
    }

    /**
     * 处理编码。
     */
    @Override
    public String skillCode() {
        return "query-ticket";
    }

    /**
     * 处理意图列表。
     */
    @Override
    public List<AgentIntent> supportedIntents() {
        return intent(AgentIntent.QUERY_TICKET);
    }
}
