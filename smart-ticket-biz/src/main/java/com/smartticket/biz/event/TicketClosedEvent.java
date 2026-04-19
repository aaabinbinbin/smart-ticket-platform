package com.smartticket.biz.event;

/**
 * 工单关闭成功事件。
 *
 * <p>该事件在关闭工单主事务提交后发布，供知识构建和向量化链路异步消费。</p>
 *
 * @param ticketId 已关闭工单 ID
 */
public record TicketClosedEvent(Long ticketId) {
}
