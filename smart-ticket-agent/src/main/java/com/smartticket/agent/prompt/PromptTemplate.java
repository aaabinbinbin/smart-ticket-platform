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
    private String code;
    private String version;
    private String model;
    private Double temperature;
    private String purpose;
    private String content;
}
