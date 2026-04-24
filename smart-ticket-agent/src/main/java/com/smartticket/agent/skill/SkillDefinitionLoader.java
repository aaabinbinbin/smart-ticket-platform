package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * 从 classpath:agent/skills/*.md 加载技能定义，解析 YAML frontmatter 并绑定 Tool Bean。
 */
@Component
public class SkillDefinitionLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillDefinitionLoader.class);
    private static final String SKILLS_LOCATION = "classpath*:agent/skills/*.md";

    private final ApplicationContext applicationContext;
    private final Yaml yaml;

    @Autowired
    public SkillDefinitionLoader(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.yaml = new Yaml();
    }

    /**
     * 加载全部技能定义。
     *
     * @return 技能列表
     */
    public List<AgentSkill> loadSkills() {
        List<AgentSkill> skills = new ArrayList<>();
        try {
            Resource[] resources = applicationContext.getResources(SKILLS_LOCATION);
            for (Resource resource : resources) {
                try {
                    DefinitionDrivenSkill skill = loadSkill(resource);
                    if (skill != null) {
                        skills.add(skill);
                    }
                } catch (Exception e) {
                    log.warn("跳过无效的技能定义文件: {}, 原因: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("扫描技能定义文件失败", e);
        }
        return skills;
    }

    @SuppressWarnings("unchecked")
    private DefinitionDrivenSkill loadSkill(Resource resource) throws Exception {
        String content;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            content = sb.toString();
        }

        // 解析 YAML frontmatter (--- ... ---)
        if (!content.startsWith("---\n")) {
            return null;
        }
        int endIndex = content.indexOf("---\n", 4);
        if (endIndex == -1) {
            return null;
        }
        String yamlBlock = content.substring(4, endIndex);

        Map<String, Object> raw = yaml.load(yamlBlock);
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        SkillDefinition definition = new SkillDefinition();

        definition.setSkillCode(str(raw.get("skillCode")));
        definition.setSkillName(str(raw.get("skillName")));
        definition.setDescription(str(raw.get("description")));
        definition.setRiskLevel(parseRiskLevel(raw.get("riskLevel")));
        definition.setCanAutoExecute(bool(raw.get("canAutoExecute")));
        definition.setToolBeanName(str(raw.get("toolBeanName")));
        definition.setRequiredPermissions(parseStringList(raw.get("requiredPermissions")));
        definition.setSupportedIntents(parseIntentList(raw.get("supportedIntents")));

        AgentTool tool = applicationContext.getBean(definition.getToolBeanName(), AgentTool.class);
        return new DefinitionDrivenSkill(definition, tool);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static ToolRiskLevel parseRiskLevel(Object value) {
        if (value == null) return null;
        try {
            return ToolRiskLevel.valueOf(value.toString().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<AgentIntent> parseIntentList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List) {
            List<AgentIntent> result = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                if (item != null) {
                    try {
                        result.add(AgentIntent.valueOf(item.toString().trim()));
                    } catch (IllegalArgumentException e) {
                        log.warn("未知的意图: {}", item);
                    }
                }
            }
            return result;
        }
        return List.of();
    }
}
