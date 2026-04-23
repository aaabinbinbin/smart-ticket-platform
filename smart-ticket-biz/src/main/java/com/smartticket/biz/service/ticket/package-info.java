/**
 * ticket 子域核心服务。
 *
 * <p>建议阅读顺序：
 * {@link com.smartticket.biz.service.ticket.TicketService} 作为上层统一入口；
 * command/query/workflow/comment 等服务分别负责创建、查询、流转和评论；
 * support 类只保留跨服务复用的校验、日志和事务后置动作。</p>
 */
package com.smartticket.biz.service.ticket;
