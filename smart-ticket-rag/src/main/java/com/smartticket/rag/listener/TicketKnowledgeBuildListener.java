package com.smartticket.rag.listener;

import com.smartticket.biz.event.TicketClosedEvent;
import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.mapper.TicketKnowledgeBuildTaskMapper;
import com.smartticket.rag.config.KnowledgeBuildRabbitConfig;
import com.smartticket.rag.mq.KnowledgeBuildMessage;
import com.smartticket.rag.service.TicketKnowledgeBuildTaskProcessor;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 工单关闭后知识构建的消息监听与补偿投递组件。
 */
@Component
@ConditionalOnProperty(prefix = "smart-ticket.knowledge.rabbit", name = "enabled", havingValue = "true")
public class TicketKnowledgeBuildListener {
    private static final Logger log = LoggerFactory.getLogger(TicketKnowledgeBuildListener.class);

    /**
     * RabbitMQ 发送模板，用于投递知识构建消息。
     */
    private final RabbitTemplate rabbitTemplate;

    /**
     * 知识构建任务 Mapper，用于查询待投递任务。
     */
    private final TicketKnowledgeBuildTaskMapper taskMapper;

    /**
     * 知识构建任务处理器，用于消费消息后执行实际构建。
     */
    private final TicketKnowledgeBuildTaskProcessor taskProcessor;

    /**
     * 每次补偿投递扫描的最大任务数。
     */
    private final int relayBatchSize;

    /**
     * 创建知识构建消息监听器。
     *
     * @param rabbitTemplate RabbitMQ 模板
     * @param taskMapper 知识构建任务 Mapper
     * @param taskProcessor 知识构建任务处理器
     * @param relayBatchSize 补偿投递批大小
     */
    public TicketKnowledgeBuildListener(
            RabbitTemplate rabbitTemplate,
            TicketKnowledgeBuildTaskMapper taskMapper,
            TicketKnowledgeBuildTaskProcessor taskProcessor,
            @Value("${smart-ticket.knowledge.task.relay-batch-size:20}") int relayBatchSize
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.taskMapper = taskMapper;
        this.taskProcessor = taskProcessor;
        this.relayBatchSize = relayBatchSize;
    }

    /**
     * 监听工单关闭领域事件，并把对应知识构建任务投递到 RabbitMQ。
     *
     * @param event 工单关闭事件
     */
    @EventListener
    public void onTicketClosed(TicketClosedEvent event) {
        Long taskId = event.knowledgeBuildTaskId();
        if (taskId == null) {
            // 兼容旧事件结构：如果事件里没有 taskId，则按 ticketId 回查任务。
            TicketKnowledgeBuildTask task = taskMapper.findByTicketId(event.ticketId());
            taskId = task == null ? null : task.getId();
        }
        if (taskId == null) {
            log.warn("收到工单关闭事件时未找到知识构建任务：ticketId={}", event.ticketId());
            return;
        }
        publish(taskId, event.ticketId());
    }

    /**
     * 消费 RabbitMQ 中的知识构建消息。
     *
     * @param message 知识构建消息
     */
    @RabbitListener(queues = KnowledgeBuildRabbitConfig.QUEUE)
    public void onMessage(KnowledgeBuildMessage message) {
        if (message == null || message.getTaskId() == null) {
            log.warn("非法的知识构建消息：{}", message);
            return;
        }
        taskProcessor.process(message.getTaskId());
    }

    /**
     * 定时补偿投递未完成或待重试的知识构建任务。
     */
    @Scheduled(fixedDelayString = "${smart-ticket.knowledge.task.relay-fixed-delay-ms:30000}")
    public void relayPendingTasks() {
        List<TicketKnowledgeBuildTask> tasks = taskMapper.findDispatchable(LocalDateTime.now(), relayBatchSize);
        for (TicketKnowledgeBuildTask task : tasks) {
            try {
                publish(task.getId(), task.getTicketId());
            } catch (RuntimeException ex) {
                log.warn("知识构建任务投递失败：taskId={}, reason={}", task.getId(), ex.getMessage());
            }
        }
    }

    /**
     * 向 RabbitMQ 投递单个知识构建任务消息。
     *
     * @param taskId 知识构建任务 ID
     * @param ticketId 工单 ID
     */
    private void publish(Long taskId, Long ticketId) {
        KnowledgeBuildMessage message = KnowledgeBuildMessage.builder()
                .taskId(taskId)
                .ticketId(ticketId)
                .build();
        rabbitTemplate.convertAndSend(
                KnowledgeBuildRabbitConfig.EXCHANGE,
                KnowledgeBuildRabbitConfig.ROUTING_KEY,
                message
        );
        log.info("知识构建任务已发布：taskId={}, ticketId={}", taskId, ticketId);
    }
}
