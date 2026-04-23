package com.smartticket.agent.eval;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentEvalCase {
    private String id;
    private String category;
    private String input;
    private AgentIntent expectedIntent;
    private boolean expectedNeedClarify;
    private String expectedSkill;
    private List<AgentToolParameterField> expectedKeySlots = new ArrayList<>();
    private String expectedOutcome;
}
