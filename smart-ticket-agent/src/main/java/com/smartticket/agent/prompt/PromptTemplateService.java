package com.smartticket.agent.prompt;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * 提示词模板服务。
 */
@Service
public class PromptTemplateService {
    private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    /**
     * 处理加载。
     */
    public PromptTemplate load(String code) {
        return cache.computeIfAbsent(code, this::loadFromClasspath);
    }

    /**
     * 处理内容。
     */
    public String content(String code, String fallback) {
        PromptTemplate template = load(code);
        return template == null || template.getContent() == null || template.getContent().isBlank()
                ? fallback
                : template.getContent();
    }

    /**
     * 处理版本。
     */
    public String version(String code) {
        PromptTemplate template = load(code);
        return template == null ? "inline-fallback" : template.getVersion();
    }

    /**
     * 加载FromClasspath。
     */
    private PromptTemplate loadFromClasspath(String code) {
        String path = "agent/prompt/" + code + ".md";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return null;
            }
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return PromptTemplate.builder()
                    .code(code)
                    .version(extract(content, "version:", "v1"))
                    .purpose(extract(content, "purpose:", code))
                    .content(removeHeader(content))
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 提取工具参数。
     */
    private String extract(String content, String prefix, String fallback) {
        return content.lines()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .map(line -> line.substring(prefix.length()).trim())
                .orElse(fallback);
    }

    /**
     * 移除Header。
     */
    private String removeHeader(String content) {
        return content.lines()
                .filter(line -> !line.startsWith("version:") && !line.startsWith("purpose:"))
                .reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right)
                .trim();
    }
}
