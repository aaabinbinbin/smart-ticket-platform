package com.smartticket.agent.execution;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.biz.model.CurrentUser;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Agent 执行策略服务。
 *
 * <p>该服务位于意图路由之后、具体执行器之前，用于根据 intent、风险等级、允许暴露的工具、
 * 是否需要确认以及预算上限，决定本轮应该走哪种执行模式。它不执行工具、不做数据库写入，
 * 也不会修改 session、memory、pendingAction 或 trace。</p>
 */
@Service
public class AgentExecutionPolicyService {
    /**
     * 查询与历史检索场景统一采用较宽的只读超时。
     */
    private static final Duration READ_ONLY_TIMEOUT = Duration.ofSeconds(120);

    /**
     * 写命令默认采用更短超时，避免写链长时间阻塞。
     */
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(30);

    private final SkillRegistry skillRegistry;

    public AgentExecutionPolicyService(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 根据当前用户和路由结果解析执行策略。
     *
     * <p>当前阶段先把 intent、risk、allowedTools、confirmation、budget 收敛为结构化策略，
     * 供 AgentFacade 和 SpringAiToolSupport 统一读取。预算字段暂不强制执行，只承担显式策略表达职责。</p>
     *
     * @param currentUser 当前登录用户
     * @param route 当前意图路由结果
     * @return 本轮执行策略
     */
    public AgentExecutionPolicy resolve(CurrentUser currentUser, IntentRoute route) {
        if (route == null || route.getIntent() == null || route.getConfidence() < 0.50d) {
            return clarificationPolicy();
        }
        return switch (route.getIntent()) {
            case QUERY_TICKET, SEARCH_HISTORY -> readOnlyPolicy(currentUser, route);
            case CREATE_TICKET -> createTicketPolicy(currentUser, route);
            case TRANSFER_TICKET -> transferTicketPolicy(currentUser, route);
        };
    }

    private AgentExecutionPolicy clarificationPolicy() {
        return AgentExecutionPolicy.builder()
                .mode(AgentExecutionMode.CLARIFICATION)
                .allowReact(false)
                .allowAutoExecute(false)
                .requireConfirmation(false)
                .maxRiskLevel(ToolRiskLevel.READ_ONLY)
                .timeout(Duration.ofSeconds(10))
                .maxLlmCalls(0)
                .maxToolCalls(0)
                .maxRagCalls(0)
                .build();
    }

    private AgentExecutionPolicy readOnlyPolicy(CurrentUser currentUser, IntentRoute route) {
        List<AgentSkill> allowedSkills = loadAllowedSkills(currentUser, route.getIntent(), ToolRiskLevel.READ_ONLY);
        return AgentExecutionPolicy.builder()
                .mode(AgentExecutionMode.READ_ONLY_REACT)
                .allowedSkills(allowedSkills)
                .allowedToolNames(toToolNames(allowedSkills))
                .allowReact(!allowedSkills.isEmpty())
                .allowAutoExecute(true)
                .requireConfirmation(false)
                .maxRiskLevel(ToolRiskLevel.READ_ONLY)
                .timeout(READ_ONLY_TIMEOUT)
                .maxLlmCalls(1)
                .maxToolCalls(4)
                .maxRagCalls(route.getIntent() == AgentIntent.SEARCH_HISTORY ? 1 : 0)
                .build();
    }

    private AgentExecutionPolicy createTicketPolicy(CurrentUser currentUser, IntentRoute route) {
        List<AgentSkill> allowedSkills = loadAllowedSkills(currentUser, route.getIntent(), ToolRiskLevel.LOW_RISK_WRITE);
        AgentSkill skill = resolvePrimarySkill(route.getIntent(), allowedSkills);
        return AgentExecutionPolicy.builder()
                .mode(AgentExecutionMode.WRITE_COMMAND_EXECUTE)
                .allowedSkills(allowedSkills)
                .allowedToolNames(skill == null ? List.of() : List.of(skill.tool().name()))
                .allowReact(false)
                .allowAutoExecute(skill != null && skill.canAutoExecute())
                .requireConfirmation(skill != null && skill.tool().metadata().isRequireConfirmation())
                .maxRiskLevel(ToolRiskLevel.LOW_RISK_WRITE)
                .timeout(WRITE_TIMEOUT)
                .maxLlmCalls(0)
                .maxToolCalls(1)
                .maxRagCalls(1)
                .build();
    }

    private AgentExecutionPolicy transferTicketPolicy(CurrentUser currentUser, IntentRoute route) {
        List<AgentSkill> allowedSkills = loadAllowedSkills(currentUser, route.getIntent(), ToolRiskLevel.HIGH_RISK_WRITE);
        AgentSkill skill = resolvePrimarySkill(route.getIntent(), allowedSkills);
        boolean requireConfirmation = skill == null || skill.tool().metadata().isRequireConfirmation();
        return AgentExecutionPolicy.builder()
                .mode(requireConfirmation
                        ? AgentExecutionMode.HIGH_RISK_CONFIRMATION
                        : AgentExecutionMode.WRITE_COMMAND_EXECUTE)
                .allowedSkills(allowedSkills)
                .allowedToolNames(skill == null ? List.of() : List.of(skill.tool().name()))
                .allowReact(false)
                .allowAutoExecute(skill != null && skill.canAutoExecute() && !requireConfirmation)
                .requireConfirmation(requireConfirmation)
                .maxRiskLevel(ToolRiskLevel.HIGH_RISK_WRITE)
                .timeout(WRITE_TIMEOUT)
                .maxLlmCalls(0)
                .maxToolCalls(1)
                .maxRagCalls(0)
                .build();
    }

    private List<AgentSkill> loadAllowedSkills(
            CurrentUser currentUser,
            AgentIntent intent,
            ToolRiskLevel maxRisk
    ) {
        List<String> permissions = currentUser == null ? null : currentUser.getRoles();
        List<AgentSkill> available = skillRegistry.findAvailable(intent, permissions, maxRisk);
        if (!available.isEmpty()) {
            return available;
        }
        return skillRegistry.findByIntent(intent).map(List::of).orElse(List.of());
    }

    private AgentSkill resolvePrimarySkill(AgentIntent intent, List<AgentSkill> allowedSkills) {
        if (allowedSkills != null && !allowedSkills.isEmpty()) {
            return allowedSkills.get(0);
        }
        return skillRegistry.findByIntent(intent).orElse(null);
    }

    private List<String> toToolNames(List<AgentSkill> allowedSkills) {
        if (allowedSkills == null) {
            return List.of();
        }
        return allowedSkills.stream()
                .filter(skill -> skill.tool() != null)
                .map(skill -> skill.tool().name())
                .distinct()
                .toList();
    }
}
