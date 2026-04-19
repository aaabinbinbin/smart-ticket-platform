package com.smartticket.infra.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 对话模型配置。
 *
 * <p>本配置只负责把 Spring AI 自动装配出的 {@link ChatModel} 包装成项目统一使用的
 * {@link ChatClient}。Agent 层可以注入 ChatClient 发起模型交互，但工单状态流转、
 * 权限校验、幂等和日志等业务规则仍然必须保留在 biz 模块。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ChatClientConfig {

    /**
     * 创建项目默认 ChatClient。
     *
     * <p>只有在显式开启 smart-ticket.ai.chat.enabled=true 且 Spring AI 已经创建
     * ChatModel 时才注册，避免本地没有模型配置时影响应用启动。</p>
     *
     * @param chatModel Spring AI 自动装配出的对话模型
     * @return 项目默认对话客户端
     */
    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnProperty(prefix = "smart-ticket.ai.chat", name = "enabled", havingValue = "true")
    public ChatClient smartTicketChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
