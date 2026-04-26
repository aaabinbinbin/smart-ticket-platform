package com.smartticket.agent.prompt;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * Prompt 模板服务，从 classpath:prompts/*.md 加载 YAML frontmatter + Markdown 正文。
 */
@Service
public class PromptTemplateService {
    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);
    private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    public PromptTemplate load(String code) {
        return cache.computeIfAbsent(code, this::loadFromClasspath);
    }

    public String content(String code, String fallback) {
        PromptTemplate template = load(code);
        return template == null || template.getContent() == null || template.getContent().isBlank()
                ? fallback
                : template.getContent();
    }

    public String version(String code) {
        PromptTemplate template = load(code);
        return template == null ? "inline-fallback" : template.getVersion();
    }

    private PromptTemplate loadFromClasspath(String code) {
        String path = "prompts/" + code + ".md";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null;
        }
        try {
            String raw = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return parseTemplate(code, raw);
        } catch (Exception ex) {
            log.warn("加载 prompt 模板失败: code={}, path={}", code, path);
            return null;
        }
    }

    private PromptTemplate parseTemplate(String code, String raw) {
        String body = raw;
        String version = "v1";
        String model = null;
        Double temperature = null;

        // 解析 YAML frontmatter（--- 包裹）
        if (raw.startsWith("---")) {
            int end = raw.indexOf("---", 3);
            if (end > 0) {
                String fm = raw.substring(3, end).trim();
                for (String line : fm.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("version:"))
                        version = line.substring(8).trim().replace("\"", "");
                    else if (line.startsWith("model:"))
                        model = line.substring(6).trim().replace("\"", "");
                    else if (line.startsWith("temperature:"))
                        try { temperature = Double.parseDouble(line.substring(12).trim()); } catch (NumberFormatException ignored) {}
                }
                body = raw.substring(end + 3).trim();
            }
        }

        return PromptTemplate.builder()
                .code(code).version(version).model(model).temperature(temperature)
                .content(body).build();
    }
}
