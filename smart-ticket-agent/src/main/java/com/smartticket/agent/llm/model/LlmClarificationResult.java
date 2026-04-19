package com.smartticket.agent.llm.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 缺参澄清结构化输出。
 *
 * <p>当 Tool 返回 NEED_MORE_INFO 时，用该对象承载模型生成的追问文本。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmClarificationResult {
    /**
     * 面向用户的一句话澄清问题。
     */
    private String question;

    /**
     * 本次追问涉及的缺失字段名，主要用于日志排查和后续扩展。
     */
    @Builder.Default
    private List<String> missingFields = new ArrayList<>();
}
