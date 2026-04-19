package com.smartticket.agent.llm.client;

import java.util.List;

/**
 * LLM 客户端抽象。
 *
 * <p>Agent 业务层只依赖该接口，避免把具体模型供应商的 SDK 细节扩散到路由和 Tool 调用链。</p>
 */
public interface LlmClient {
    /**
     * 判断当前客户端是否具备真实调用条件。
     *
     * <p>配置缺失、显式关闭或适配器不可用时返回 false，上层会回退到规则链路。</p>
     */
    boolean isAvailable();

    /**
     * 发起一次 Chat Completion 调用，并返回模型原始文本输出。
     *
     * @param messages 已构造好的 system/user 消息
     * @return 模型返回的文本，通常要求是 JSON 字符串
     */
    String complete(List<LlmMessage> messages);
}
