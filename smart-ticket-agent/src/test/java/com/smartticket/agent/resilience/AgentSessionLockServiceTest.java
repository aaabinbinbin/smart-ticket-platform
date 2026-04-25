package com.smartticket.agent.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentSessionLockService} 高压互斥测试。
 *
 * <p>该测试保护 P6 的核心约束：同一个 sessionId 并发进入时只能有一个请求继续主链，
 * 避免 pendingAction、activeTicketId 和 recentMessages 被并发写乱。</p>
 */
class AgentSessionLockServiceTest {

    @Test
    void tryLockShouldAllowOnlyOneConcurrentRequestForSameSession() throws InterruptedException {
        AgentSessionLockService service = new AgentSessionLockService();
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(10);
        AtomicInteger acquired = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (service.tryLock("s-concurrent")) {
                        acquired.incrementAndGet();
                        release.await();
                        service.unlock("s-concurrent");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    finish.countDown();
                }
            });
            thread.start();
        }

        ready.await();
        start.countDown();
        Thread.sleep(50L);
        assertEquals(1, acquired.get());
        release.countDown();
        finish.await();
    }
}
