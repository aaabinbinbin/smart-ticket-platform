package com.smartticket.rag.mq;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识构建 RabbitMQ 消息体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBuildMessage implements Serializable {
    /**
     * 知识构建任务 ID。
     */
    private Long taskId;

    /**
     * 来源工单 ID。
     */
    private Long ticketId;
}
