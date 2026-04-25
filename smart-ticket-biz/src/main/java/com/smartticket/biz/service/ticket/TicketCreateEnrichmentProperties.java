package com.smartticket.biz.service.ticket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 工单创建字段补全配置。
 *
 * <p>控制 LLM enrichment 开关、超时、最低置信度等参数。
 * LLM enrichment 默认关闭，避免本地没有 API Key 时影响项目启动。</p>
 */
@ConfigurationProperties(prefix = "smart-ticket.ticket.enrichment")
public class TicketCreateEnrichmentProperties {

    /** 是否启用 LLM enrichment。默认关闭。 */
    private boolean llmEnabled = false;

    /** LLM 调用超时（毫秒）。推荐 2~3 秒，避免拖慢创建工单主链。 */
    private long llmTimeoutMs = 3000;

    /** LLM 调用最大重试次数。默认 0 表示不重试。 */
    private int llmMaxRetry = 0;

    /** LLM 返回结果的最低置信度。低于此值时不使用 LLM 结果。 */
    private double llmMinConfidence = 0.6;

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public long getLlmTimeoutMs() {
        return llmTimeoutMs;
    }

    public void setLlmTimeoutMs(long llmTimeoutMs) {
        this.llmTimeoutMs = llmTimeoutMs;
    }

    public int getLlmMaxRetry() {
        return llmMaxRetry;
    }

    public void setLlmMaxRetry(int llmMaxRetry) {
        this.llmMaxRetry = llmMaxRetry;
    }

    public double getLlmMinConfidence() {
        return llmMinConfidence;
    }

    public void setLlmMinConfidence(double llmMinConfidence) {
        this.llmMinConfidence = llmMinConfidence;
    }
}
