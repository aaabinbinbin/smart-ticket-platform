package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 智能体技能注册表，作为意图到技能的统一能力分发入口。
 */
@Component
public class SkillRegistry {
    /**
     * 当前注册的全部技能，按技能编码排序以保证选择结果稳定。
     */
    private final List<AgentSkill> skills;

    /**
     * 生产环境构造：通过 SkillDefinitionLoader 从 markdown 文件加载技能。
     */
    @Autowired
    public SkillRegistry(SkillDefinitionLoader loader) {
        this.skills = loader.loadSkills().stream()
                .sorted(Comparator.comparing(AgentSkill::skillCode))
                .toList();
    }

    /**
     * 测试环境构造：直接传入技能列表。
     */
    public SkillRegistry(List<AgentSkill> skills) {
        this.skills = skills.stream()
                .sorted(Comparator.comparing(AgentSkill::skillCode))
                .toList();
    }

    /**
     * 按业务意图查找第一个可支持的技能。
     *
     * @param intent 当前识别出的业务意图
     * @return 匹配到的技能
     */
    public Optional<AgentSkill> findByIntent(AgentIntent intent) {
        return skills.stream()
                .filter(skill -> skill.supports(intent))
                .findFirst();
    }

    /**
     * 按业务意图获取技能，未找到时直接抛出异常。
     *
     * @param intent 当前识别出的业务意图
     * @return 必然存在的技能
     */
    public AgentSkill requireByIntent(AgentIntent intent) {
        return findByIntent(intent)
                .orElseThrow(() -> new IllegalStateException("No agent skill found for intent: " + intent));
    }

    /**
     * 根据 Tool 名称反查绑定该 Tool 的技能。
     *
     * @param toolName Tool 的唯一名称
     * @return 匹配到的技能
     */
    public Optional<AgentSkill> findByToolName(String toolName) {
        return skills.stream()
                .filter(skill -> skill.tool() != null && skill.tool().name().equals(toolName))
                .findFirst();
    }

    /**
     * 根据 Tool 名称反查技能，未找到时直接抛出异常。
     *
     * @param toolName Tool 的唯一名称
     * @return 必然存在的技能
     */
    public AgentSkill requireByToolName(String toolName) {
        return findByToolName(toolName)
                .orElseThrow(() -> new IllegalStateException("No agent skill found for tool: " + toolName));
    }

    /**
     * 根据意图、权限和最高风险等级筛选当前用户可用的技能。
     *
     * @param intent 目标业务意图，允许为空表示不过滤意图
     * @param permissions 当前用户权限集合，允许为空表示不过滤权限
     * @param maxRiskLevel 允许执行的最高风险等级，允许为空表示不过滤风险
     * @return 满足条件的技能列表
     */
    public List<AgentSkill> findAvailable(AgentIntent intent, List<String> permissions, ToolRiskLevel maxRiskLevel) {
        return skills.stream()
                .filter(skill -> intent == null || skill.supports(intent))
                .filter(skill -> permissions == null || permissions.containsAll(skill.requiredPermissions()))
                .filter(skill -> maxRiskLevel == null || skill.riskLevel().ordinal() <= maxRiskLevel.ordinal())
                .toList();
    }

    /**
     * 返回当前注册的全部技能。
     *
     * @return 技能列表
     */
    public List<AgentSkill> allSkills() {
        return skills;
    }
}
