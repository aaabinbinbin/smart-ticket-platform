package com.smartticket.agent.react;

import com.smartticket.agent.execution.AgentExecutionPolicy;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.tool.core.AgentTool;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 只读 ReAct 工具目录。
 *
 * <p>该组件位于 Agent 执行策略与只读 ReAct 执行器之间，负责把当前轮 policy 允许的技能收敛成一组
 * 可以安全暴露给模型的只读工具。它只做工具暴露裁剪，不执行工具、不修改 session/memory/pendingAction/trace。
 * 即使上游 policy 误把写技能放进 allowedSkills，这里也会再次过滤，避免 ReAct 越权看到写工具。</p>
 */
@Component
public class AgentReactToolCatalog {

    /**
     * 根据当前轮执行策略构建可暴露给只读 ReAct 的工具数组。
     *
     * @param policy 当前轮执行策略，提供 allowedSkills
     * @return 仅包含只读工具的对象数组；当没有可暴露工具时返回空数组
     */
    public Object[] buildTools(AgentExecutionPolicy policy) {
        return exposedTools(policy).toArray();
    }

    /**
     * 根据当前轮执行策略返回真正允许透传到 ToolContext 的工具白名单。
     *
     * <p>这里与 {@link #buildTools(AgentExecutionPolicy)} 共用同一套过滤逻辑，保证“模型看到的工具集合”和
     * “后端执行期允许的工具集合”完全一致，避免出现展示与校验不一致的漏洞。</p>
     *
     * @param policy 当前轮执行策略
     * @return 只读工具名称白名单
     */
    public List<String> allowedToolNames(AgentExecutionPolicy policy) {
        return exposedTools(policy).stream()
                .map(AgentTool::name)
                .distinct()
                .toList();
    }

    /**
     * 判断某个技能是否允许暴露给只读 ReAct。
     *
     * @param skill 待判断的技能
     * @return true 表示该技能绑定的是只读工具，可安全暴露给模型
     */
    public boolean canExpose(AgentSkill skill) {
        if (skill == null || skill.tool() == null || skill.tool().metadata() == null) {
            return false;
        }
        // P5 的核心约束是“只读 ReAct 只暴露只读工具”，因此这里直接以 Tool 元数据为最终裁决。
        return skill.tool().metadata().isReadOnly();
    }

    private List<AgentTool> exposedTools(AgentExecutionPolicy policy) {
        List<AgentSkill> allowedSkills = policy == null ? List.of() : policy.getAllowedSkills();
        if (allowedSkills == null || allowedSkills.isEmpty()) {
            return List.of();
        }
        return allowedSkills.stream()
                .filter(this::canExpose)
                .map(AgentSkill::tool)
                .distinct()
                .toList();
    }
}
