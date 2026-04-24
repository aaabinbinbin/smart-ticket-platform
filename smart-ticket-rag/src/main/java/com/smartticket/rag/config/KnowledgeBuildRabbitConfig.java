package com.smartticket.rag.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工单关闭后知识构建使用的 RabbitMQ 交换机、队列和绑定配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "smart-ticket.knowledge.rabbit", name = "enabled", havingValue = "true")
public class KnowledgeBuildRabbitConfig {
    /**
     * 知识构建专用交换机名称。
     */
    public static final String EXCHANGE = "smart-ticket.knowledge.exchange";

    /**
     * 知识构建任务队列名称。
     */
    public static final String QUEUE = "smart-ticket.knowledge.build.queue";

    /**
     * 工单关闭触发知识构建的路由键。
     */
    public static final String ROUTING_KEY = "knowledge.build.closed-ticket";

    /**
     * 声明持久化直连交换机。
     *
     * @return RabbitMQ 交换机
     */
    @Bean
    public DirectExchange knowledgeBuildExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /**
     * 声明持久化知识构建队列。
     *
     * @return RabbitMQ 队列
     */
    @Bean
    public Queue knowledgeBuildQueue() {
        return new Queue(QUEUE, true);
    }

    /**
     * 绑定知识构建队列到交换机。
     *
     * @param knowledgeBuildQueue 知识构建队列
     * @param knowledgeBuildExchange 知识构建交换机
     * @return RabbitMQ 绑定关系
     */
    @Bean
    public Binding knowledgeBuildBinding(Queue knowledgeBuildQueue, DirectExchange knowledgeBuildExchange) {
        return BindingBuilder.bind(knowledgeBuildQueue).to(knowledgeBuildExchange).with(ROUTING_KEY);
    }
}
