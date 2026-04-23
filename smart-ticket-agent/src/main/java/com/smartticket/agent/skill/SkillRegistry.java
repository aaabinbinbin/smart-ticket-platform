package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SkillRegistry {
    private final List<AgentSkill> skills;

    public SkillRegistry(List<AgentSkill> skills) {
        this.skills = skills.stream()
                .sorted(Comparator.comparing(AgentSkill::skillCode))
                .toList();
    }

    public Optional<AgentSkill> findByIntent(AgentIntent intent) {
        return skills.stream()
                .filter(skill -> skill.supports(intent))
                .findFirst();
    }

    public AgentSkill requireByIntent(AgentIntent intent) {
        return findByIntent(intent)
                .orElseThrow(() -> new IllegalStateException("No agent skill found for intent: " + intent));
    }

    public List<AgentSkill> findAvailable(AgentIntent intent, List<String> permissions, ToolRiskLevel maxRiskLevel) {
        return skills.stream()
                .filter(skill -> intent == null || skill.supports(intent))
                .filter(skill -> permissions == null || permissions.containsAll(skill.requiredPermissions()))
                .filter(skill -> maxRiskLevel == null || skill.riskLevel().ordinal() <= maxRiskLevel.ordinal())
                .toList();
    }

    public List<AgentSkill> allSkills() {
        return skills;
    }
}
