package com.smartticket.agent.orchestration;

import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRegistry;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具调用计划校验器。
 *
 * <p>该类只校验“计划是否允许进入执行阶段”，不执行 Tool，也不调用 biz 层。</p>
 */
@Component
public class ToolCallPlanValidator {
    private static final Logger log = LoggerFactory.getLogger(ToolCallPlanValidator.class);

    /**
     * Tool 注册表，用于校验 toolName 是否存在以及 Tool 是否支持 intent。
     */
    private final AgentToolRegistry toolRegistry;

    public ToolCallPlanValidator(AgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 校验工具调用计划。
     */
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
        if (requiresConfirmation(tool) && !containsConfirmation(message)) {
            log.info("agent plan requires confirmation before execution: plan={}, tool={}", plan, tool.name());
            return ToolCallPlanValidationResult.needConfirmation(tool, "该操作风险较高，请明确确认后再继续。");
        }
        return ToolCallPlanValidationResult.valid(tool);
    }

    /**
     * 判断 Tool 是否属于必须确认的高风险写操作。
     */
    private boolean requiresConfirmation(AgentTool tool) {
        return tool.metadata().getRiskLevel() == ToolRiskLevel.HIGH_RISK_WRITE
                && tool.metadata().isRequireConfirmation();
    }

    /**
     * 判断用户消息中是否包含明确确认语义。
     */
    private boolean containsConfirmation(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("确认")
                || normalized.contains("同意")
                || normalized.contains("执行")
                || normalized.contains("confirm")
                || normalized.contains("yes");
    }
}
