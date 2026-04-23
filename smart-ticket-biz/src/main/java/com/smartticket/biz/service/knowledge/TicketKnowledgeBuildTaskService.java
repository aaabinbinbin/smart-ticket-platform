package com.smartticket.biz.service.knowledge;

import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.mapper.TicketKnowledgeBuildTaskMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class TicketKnowledgeBuildTaskService {
    public static final String TASK_TYPE_CLOSED_TICKET = "CLOSED_TICKET";
    public static final String STATUS_PENDING = "PENDING";

    private final TicketKnowledgeBuildTaskMapper taskMapper;

    public TicketKnowledgeBuildTaskService(TicketKnowledgeBuildTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

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

    public TicketKnowledgeBuildTask findByTicketId(Long ticketId) {
        return taskMapper.findByTicketId(ticketId);
    }
}
