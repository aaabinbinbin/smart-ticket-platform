package com.smartticket.rag.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识构建 RabbitMQ 配置。
 *
 * <p>该配置负责声明知识构建使用的交换机、队列、绑定关系，以及 RabbitMQ 消息序列化方式。</p>
 *
 * <p>之前使用默认 SimpleMessageConverter 时，KnowledgeBuildMessage 会走 Java 原生序列化，
 * Spring AMQP 3.x 会因为反序列化白名单限制而拒绝消费，报错：
 * Attempt to deserialize unauthorized class com.smartticket.rag.mq.KnowledgeBuildMessage。</p>
 *
 * <p>这里统一改为 Jackson2JsonMessageConverter，让生产者和消费者都使用 JSON 消息格式，
 * 避免 Java 原生反序列化的安全限制问题。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "smart-ticket.knowledge.rabbit", name = "enabled", havingValue = "true")
public class KnowledgeBuildRabbitConfig {

    /**
     * 知识构建交换机名称。
     */
    public static final String EXCHANGE = "smart-ticket.knowledge.exchange";

    /**
     * 知识构建队列名称。
     */
    public static final String QUEUE = "smart-ticket.knowledge.build.queue";

    /**
     * 工单关闭后触发知识构建的路由键。
     */
    public static final String ROUTING_KEY = "knowledge.build.closed-ticket";

    /**
     * 声明知识构建交换机。
     *
     * @return DirectExchange
     */
    @Bean
    public DirectExchange knowledgeBuildExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /**
     * 声明知识构建队列。
     *
     * @return Queue
     */
    @Bean
    public Queue knowledgeBuildQueue() {
        return new Queue(QUEUE, true);
    }

    /**
     * 绑定知识构建队列和交换机。
     *
     * @param knowledgeBuildQueue    知识构建队列
     * @param knowledgeBuildExchange 知识构建交换机
     * @return Binding
     */
    @Bean
    public Binding knowledgeBuildBinding(Queue knowledgeBuildQueue, DirectExchange knowledgeBuildExchange) {
        return BindingBuilder
                .bind(knowledgeBuildQueue)
                .to(knowledgeBuildExchange)
                .with(ROUTING_KEY);
    }

    /**
     * RabbitMQ JSON 消息转换器。
     *
     * <p>使用具体类型 Jackson2JsonMessageConverter，而不是 MessageConverter 接口类型，
     * 可以避免导包错误或 Bean 类型识别问题。</p>
     *
     * @return Jackson2JsonMessageConverter
     */
    @Bean
    public Jackson2JsonMessageConverter knowledgeBuildJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate 配置。
     *
     * <p>生产者通过 RabbitTemplate 发送 KnowledgeBuildMessage 时，会使用 JSON 格式写入 RabbitMQ，
     * 不再使用 Java 原生序列化。</p>
     *
     * @param connectionFactory                 RabbitMQ 连接工厂
     * @param knowledgeBuildJsonMessageConverter JSON 消息转换器
     * @return RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter knowledgeBuildJsonMessageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(knowledgeBuildJsonMessageConverter);
        return rabbitTemplate;
    }

    /**
     * RabbitListener 监听容器配置。
     *
     * <p>@RabbitListener 消费 smart-ticket.knowledge.build.queue 队列消息时，
     * 使用和 RabbitTemplate 相同的 JSON 转换器，将 JSON 消息转换为 KnowledgeBuildMessage。</p>
     *
     * @param connectionFactory                 RabbitMQ 连接工厂
     * @param knowledgeBuildJsonMessageConverter JSON 消息转换器
     * @return SimpleRabbitListenerContainerFactory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter knowledgeBuildJsonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);

        // 监听端也使用 JSON 消息转换器，避免默认 Java 反序列化被安全白名单拦截。
        factory.setMessageConverter(knowledgeBuildJsonMessageConverter);

        return factory;
    }
}