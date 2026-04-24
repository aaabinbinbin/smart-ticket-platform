package com.smartticket.agent.eval;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 智能体评测Case类。
 */
@Data
public class AgentEvalCase {
    // ID
    private String id;
    // 分类
    private String category;
    // input
    private String input;
    // expected意图
    private AgentIntent expectedIntent;
    // expectedNeedClarify
    private boolean expectedNeedClarify;
    // expected技能
    private String expectedSkill;
    private List<AgentToolParameterField> expectedKeySlots = new ArrayList<>();
    // expectedOutcome
    private String expectedOutcome;
}
