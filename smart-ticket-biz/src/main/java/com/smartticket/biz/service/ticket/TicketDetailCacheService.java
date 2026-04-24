package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketDetailDTO;
import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 工单详情缓存服务。
 *
 * <p>缓存对象是工单详情聚合结果，包含工单主信息、评论和操作日志。
 * 缓存不可作为权限依据，调用方命中缓存后仍必须做可见性判断。</p>
 */
@Service
public class TicketDetailCacheService {
    private static final Logger log = LoggerFactory.getLogger(TicketDetailCacheService.class);
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);

    // RedisJSON客户端
    private final RedisJsonClient redisJsonClient;

    /**
     * 构造工单详情缓存服务。
     */
    public TicketDetailCacheService(RedisJsonClient redisJsonClient) {
        this.redisJsonClient = redisJsonClient;
    }

    /**
     * 获取详情。
     */
    public TicketDetailDTO get(Long ticketId) {
        try {
            return redisJsonClient.get(RedisKeys.ticketDetail(ticketId), TicketDetailDTO.class);
        } catch (RuntimeException ex) {
            log.warn("读取工单详情缓存失败，ticketId={}", ticketId, ex);
            return null;
        }
    }

    /**
     * 写入数据。
     */
    public void put(Long ticketId, TicketDetailDTO detail) {
        try {
            redisJsonClient.set(RedisKeys.ticketDetail(ticketId), detail, DETAIL_TTL);
        } catch (RuntimeException ex) {
            log.warn("写入工单详情缓存失败，ticketId={}", ticketId, ex);
        }
    }

    /**
     * 清理缓存。
     */
    public void evict(Long ticketId) {
        try {
            redisJsonClient.delete(RedisKeys.ticketDetail(ticketId));
        } catch (RuntimeException ex) {
            log.warn("清理工单详情缓存失败，ticketId={}", ticketId, ex);
        }
    }
}

