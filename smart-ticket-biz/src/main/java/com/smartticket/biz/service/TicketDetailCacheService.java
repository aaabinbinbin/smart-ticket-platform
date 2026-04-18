package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cache adapter for ticket detail aggregate.
 */
@Service
public class TicketDetailCacheService {
    private static final Logger log = LoggerFactory.getLogger(TicketDetailCacheService.class);
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);

    private final RedisJsonClient redisJsonClient;

    public TicketDetailCacheService(RedisJsonClient redisJsonClient) {
        this.redisJsonClient = redisJsonClient;
    }

    public TicketDetailDTO get(Long ticketId) {
        try {
            return redisJsonClient.get(RedisKeys.ticketDetail(ticketId), TicketDetailDTO.class);
        } catch (RuntimeException ex) {
            log.warn("Read ticket detail cache failed, ticketId={}", ticketId, ex);
            return null;
        }
    }

    public void put(Long ticketId, TicketDetailDTO detail) {
        try {
            redisJsonClient.set(RedisKeys.ticketDetail(ticketId), detail, DETAIL_TTL);
        } catch (RuntimeException ex) {
            log.warn("Write ticket detail cache failed, ticketId={}", ticketId, ex);
        }
    }

    public void evict(Long ticketId) {
        try {
            redisJsonClient.delete(RedisKeys.ticketDetail(ticketId));
        } catch (RuntimeException ex) {
            log.warn("Evict ticket detail cache failed, ticketId={}", ticketId, ex);
        }
    }
}
