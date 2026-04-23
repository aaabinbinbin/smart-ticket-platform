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

@Component
@ConditionalOnProperty(prefix = "smart-ticket.knowledge.rabbit", name = "enabled", havingValue = "true")
public class TicketKnowledgeBuildListener {
    private static final Logger log = LoggerFactory.getLogger(TicketKnowledgeBuildListener.class);

    private final RabbitTemplate rabbitTemplate;
    private final TicketKnowledgeBuildTaskMapper taskMapper;
    private final TicketKnowledgeBuildTaskProcessor taskProcessor;
    private final int relayBatchSize;

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

    @EventListener
    public void onTicketClosed(TicketClosedEvent event) {
        Long taskId = event.knowledgeBuildTaskId();
        if (taskId == null) {
            TicketKnowledgeBuildTask task = taskMapper.findByTicketId(event.ticketId());
            taskId = task == null ? null : task.getId();
        }
        if (taskId == null) {
            log.warn("knowledge build task missing when ticket closed event received: ticketId={}", event.ticketId());
            return;
        }
        publish(taskId, event.ticketId());
    }

    @RabbitListener(queues = KnowledgeBuildRabbitConfig.QUEUE)
    public void onMessage(KnowledgeBuildMessage message) {
        if (message == null || message.getTaskId() == null) {
            log.warn("invalid knowledge build message: {}", message);
            return;
        }
        taskProcessor.process(message.getTaskId());
    }

    @Scheduled(fixedDelayString = "${smart-ticket.knowledge.task.relay-fixed-delay-ms:30000}")
    public void relayPendingTasks() {
        List<TicketKnowledgeBuildTask> tasks = taskMapper.findDispatchable(LocalDateTime.now(), relayBatchSize);
        for (TicketKnowledgeBuildTask task : tasks) {
            publish(task.getId(), task.getTicketId());
        }
    }

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
        log.info("knowledge build task published: taskId={}, ticketId={}", taskId, ticketId);
    }
}
