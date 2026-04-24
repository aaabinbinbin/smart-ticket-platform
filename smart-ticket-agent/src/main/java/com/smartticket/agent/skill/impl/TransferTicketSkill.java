package com.smartticket.agent.skill.impl;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AbstractToolBackedSkill;
import com.smartticket.agent.tool.ticket.TransferTicketTool;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 转派工单技能类。
 */
@Component
public class TransferTicketSkill extends AbstractToolBackedSkill {
    /**
     * 构造转派工单技能。
     */
    public TransferTicketSkill(TransferTicketTool tool) {
        super(tool);
    }

    /**
     * 处理编码。
     */
    @Override
    public String skillCode() {
        return "transfer-ticket";
    }

    /**
     * 处理意图列表。
     */
    @Override
    public List<AgentIntent> supportedIntents() {
        return intent(AgentIntent.TRANSFER_TICKET);
    }
}
