package com.smartticket.biz.service.knowledge;

import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.mapper.TicketKnowledgeBuildTaskMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 工单知识构建任务服务，负责在业务事务内创建可靠入库 task。
 */
@Service
public class TicketKnowledgeBuildTaskService {
    /**
     * 工单关闭触发的知识构建任务类型。
     */
    public static final String TASK_TYPE_CLOSED_TICKET = "CLOSED_TICKET";

    /**
     * 待处理任务状态。
     */
    public static final String STATUS_PENDING = "PENDING";

    /**
     * 知识构建任务 Mapper。
     */
    private final TicketKnowledgeBuildTaskMapper taskMapper;

    /**
     * 创建工单知识构建任务服务。
     *
     * @param taskMapper 知识构建任务 Mapper
     */
    public TicketKnowledgeBuildTaskService(TicketKnowledgeBuildTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 为指定工单创建待处理知识构建任务。
     *
     * <p>使用 insert ignore 保证同一工单重复关闭事件不会创建重复任务。</p>
     *
     * @param ticketId 工单 ID
     * @return 已创建或已存在的任务
     */
    public TicketKnowledgeBuildTask createPending(Long ticketId) {
        TicketKnowledgeBuildTask task = TicketKnowledgeBuildTask.builder()
                .ticketId(ticketId)
                .taskType(TASK_TYPE_CLOSED_TICKET)
                .status(STATUS_PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();
        taskMapper.insertIgnore(task);
        if (task.getId() != null) {
            return task;
        }
        return taskMapper.findByTicketId(ticketId);
    }

    /**
     * 按工单 ID 查询知识构建任务。
     *
     * @param ticketId 工单 ID
     * @return 知识构建任务
     */
    public TicketKnowledgeBuildTask findByTicketId(Long ticketId) {
        return taskMapper.findByTicketId(ticketId);
    }
}
