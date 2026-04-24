package com.smartticket.agent.skill.impl;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AbstractToolBackedSkill;
import com.smartticket.agent.tool.ticket.CreateTicketTool;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 创建工单技能类。
 */
@Component
public class CreateTicketSkill extends AbstractToolBackedSkill {
    /**
     * 构造创建工单技能。
     */
    public CreateTicketSkill(CreateTicketTool tool) {
        super(tool);
    }

    /**
     * 处理编码。
     */
    @Override
    public String skillCode() {
        return "create-ticket";
    }

    /**
     * 处理意图列表。
     */
    @Override
    public List<AgentIntent> supportedIntents() {
        return intent(AgentIntent.CREATE_TICKET);
    }
}
