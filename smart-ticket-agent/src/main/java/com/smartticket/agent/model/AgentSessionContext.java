package com.smartticket.agent.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 会话上下文。
 *
 * <p>当前阶段只预留 Redis 存储结构，后续用于记录多轮对话中的当前工单、
 * 最近意图和近期消息。这里不保存工单事实数据，工单事实仍以 MySQL 为准。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionContext {
    /** 当前对话正在处理的工单 ID。 */
    private Long activeTicketId;
    /** 当前对话中暂存的目标处理人 ID。 */
    private Long activeAssigneeId;
    /** 最近一次识别出的意图编码。 */
    private String lastIntent;
    /** 最近几轮用户或系统消息摘要。 */
    @Builder.Default
    private List<String> recentMessages = new ArrayList<>();
}
