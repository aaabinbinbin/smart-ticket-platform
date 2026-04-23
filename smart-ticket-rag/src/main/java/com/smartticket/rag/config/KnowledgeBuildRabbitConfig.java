package com.smartticket.rag.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "smart-ticket.knowledge.rabbit", name = "enabled", havingValue = "true")
public class KnowledgeBuildRabbitConfig {
    public static final String EXCHANGE = "smart-ticket.knowledge.exchange";
    public static final String QUEUE = "smart-ticket.knowledge.build.queue";
    public static final String ROUTING_KEY = "knowledge.build.closed-ticket";

    @Bean
    public DirectExchange knowledgeBuildExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue knowledgeBuildQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding knowledgeBuildBinding(Queue knowledgeBuildQueue, DirectExchange knowledgeBuildExchange) {
        return BindingBuilder.bind(knowledgeBuildQueue).to(knowledgeBuildExchange).with(ROUTING_KEY);
    }
}
