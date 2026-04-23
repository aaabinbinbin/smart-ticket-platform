package com.smartticket.rag.listener;

import com.smartticket.biz.event.TicketClosedEvent;
import com.smartticket.biz.service.knowledge.TicketKnowledgeService;
import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.rag.service.EmbeddingService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 工单关闭后的知识构建监听器。
 *
 * <p>监听关闭成功事件后，异步触发知识构建和向量化。失败只记录日志，后续可通过补偿任务按 ticketId
 * 重新构建。</p>
 */
@Component
public class TicketKnowledgeBuildListener {
    private static final Logger log = LoggerFactory.getLogger(TicketKnowledgeBuildListener.class);

    /** 工单知识构建服务，位于 biz 模块。 */
    private final TicketKnowledgeService ticketKnowledgeService;

    /** 知识切片和向量化服务，位于 rag 模块。 */
    private final EmbeddingService embeddingService;

    public TicketKnowledgeBuildListener(
            TicketKnowledgeService ticketKnowledgeService,
            EmbeddingService embeddingService
    ) {
        this.ticketKnowledgeService = ticketKnowledgeService;
        this.embeddingService = embeddingService;
    }

    /**
     * 异步处理工单关闭事件。
     *
     * @param event 工单关闭事件
     */
    @Async("knowledgeAsyncExecutor")
    @EventListener
    public void onTicketClosed(TicketClosedEvent event) {
        Long ticketId = event.ticketId();
        try {
            log.info("ticket knowledge async build started: ticketId={}", ticketId);
            Optional<TicketKnowledge> knowledge = ticketKnowledgeService.buildKnowledge(ticketId);
            if (knowledge.isEmpty()) {
                log.info("ticket knowledge skipped: ticketId={} not eligible", ticketId);
                return;
            }
            int chunks = embeddingService.embedKnowledge(knowledge.get()).size();
            log.info("ticket knowledge async build finished: ticketId={}, knowledgeId={}, chunks={}",
                    ticketId, knowledge.get().getId(), chunks);
        } catch (RuntimeException ex) {
            log.warn("ticket knowledge async build failed: ticketId={}, reason={}", ticketId, ex.getMessage(), ex);
        }
    }
}
