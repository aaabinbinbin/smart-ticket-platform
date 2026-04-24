package com.smartticket.agent.orchestration;

import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRegistry;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具调用计划校验器。
 */
@Component
public class ToolCallPlanValidator {
    private static final Logger log = LoggerFactory.getLogger(ToolCallPlanValidator.class);

    // 工具注册表
    private final AgentToolRegistry toolRegistry;

    /**
     * 构造工具调用计划校验器。
     */
    public ToolCallPlanValidator(AgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 校验工具调用计划是否合法且可执行。
     */
    public ToolCallPlanValidationResult validate(ToolCallPlan plan, String message) {
        Optional<AgentTool> toolOptional = toolRegistry.findByName(plan.getToolName());
        if (toolOptional.isEmpty()) {
            log.warn("智能体计划被拒绝：未找到工具，plan={}", plan);
            return ToolCallPlanValidationResult.invalid("Tool 不存在");
        }

        AgentTool tool = toolOptional.get();
        if (!tool.support(plan.getIntent())) {
            log.warn("智能体计划被拒绝：工具不支持当前意图，plan={}, tool={}", plan, tool.name());
            return ToolCallPlanValidationResult.invalid("Tool 不支持当前意图");
        }
        if (tool.metadata().getRiskLevel() == null) {
            log.warn("智能体计划被拒绝：缺少工具风险等级，plan={}, tool={}", plan, tool.name());
            return ToolCallPlanValidationResult.invalid("Tool 未声明风险等级");
        }
        if (requiresConfirmation(tool)) {
            log.info("智能体计划执行前需要确认：plan={}, tool={}", plan, tool.name());
            return ToolCallPlanValidationResult.needConfirmation(tool, "该操作风险较高，需要二次确认后才能执行。");
        }
        return ToolCallPlanValidationResult.valid(tool);
    }

    /**
     * 判断当前工具是否必须经过二次确认。
     */
    private boolean requiresConfirmation(AgentTool tool) {
        return tool.metadata().getRiskLevel() == ToolRiskLevel.HIGH_RISK_WRITE
                && tool.metadata().isRequireConfirmation();
    }
}
