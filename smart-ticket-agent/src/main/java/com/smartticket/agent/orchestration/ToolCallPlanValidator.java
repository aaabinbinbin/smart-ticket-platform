package com.smartticket.agent.orchestration;

import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRegistry;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ToolCallPlanValidator {
    private static final Logger log = LoggerFactory.getLogger(ToolCallPlanValidator.class);

    private final AgentToolRegistry toolRegistry;

    public ToolCallPlanValidator(AgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ToolCallPlanValidationResult validate(ToolCallPlan plan, String message) {
        Optional<AgentTool> toolOptional = toolRegistry.findByName(plan.getToolName());
        if (toolOptional.isEmpty()) {
            log.warn("agent plan rejected: tool not found, plan={}", plan);
            return ToolCallPlanValidationResult.invalid("Tool 不存在");
        }

        AgentTool tool = toolOptional.get();
        if (!tool.support(plan.getIntent())) {
            log.warn("agent plan rejected: tool does not support intent, plan={}, tool={}", plan, tool.name());
            return ToolCallPlanValidationResult.invalid("Tool 不支持当前意图");
        }
        if (tool.metadata().getRiskLevel() == null) {
            log.warn("agent plan rejected: missing tool risk level, plan={}, tool={}", plan, tool.name());
            return ToolCallPlanValidationResult.invalid("Tool 未声明风险等级");
        }
        if (requiresConfirmation(tool)) {
            log.info("agent plan requires confirmation before execution: plan={}, tool={}", plan, tool.name());
            return ToolCallPlanValidationResult.needConfirmation(tool, "该操作风险较高，需要二次确认后才能执行。");
        }
        return ToolCallPlanValidationResult.valid(tool);
    }

    private boolean requiresConfirmation(AgentTool tool) {
        return tool.metadata().getRiskLevel() == ToolRiskLevel.HIGH_RISK_WRITE
                && tool.metadata().isRequireConfirmation();
    }
}
