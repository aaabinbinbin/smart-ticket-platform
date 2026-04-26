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
    /** 缓存穿透防护：不存在的 ticketId 缓存一个短 TTL 的空标记 */
    private static final Duration NULL_TTL = Duration.ofSeconds(30);
    private static final TicketDetailDTO NULL_MARKER = new TicketDetailDTO();

    // RedisJSON客户端
    private final RedisJsonClient redisJsonClient;

    /**
     * 构造工单详情缓存服务。
     */
    public TicketDetailCacheService(RedisJsonClient redisJsonClient) {
        this.redisJsonClient = redisJsonClient;
    }

    /**
     * 获取详情，返回 null 时可能不存在或未命中。
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
     * 缓存不存在的 ticketId 空标记，防止穿透。
     */
    public void putNull(Long ticketId) {
        try {
            redisJsonClient.set(RedisKeys.ticketDetail(ticketId), NULL_MARKER, NULL_TTL);
        } catch (RuntimeException ex) {
            log.warn("写入空标记缓存失败，ticketId={}", ticketId, ex);
        }
    }

    /**
     * 检查是否为穿透保护的空标记。
     */
    public boolean isNullMarker(TicketDetailDTO detail) {
        return detail != null && detail.getTicket() == null;
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

