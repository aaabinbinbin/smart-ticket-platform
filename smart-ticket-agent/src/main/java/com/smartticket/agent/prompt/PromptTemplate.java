package com.smartticket.agent.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提示词模板类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate {
    // 编码
    private String code;
    // version
    private String version;
    // purpose
    private String purpose;
    // 内容
    private String content;
}
