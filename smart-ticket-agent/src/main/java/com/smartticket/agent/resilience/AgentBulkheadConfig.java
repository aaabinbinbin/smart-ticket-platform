package com.smartticket.agent.resilience;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 慢资源隔离配置，使用有界队列防止 OOM。
 */
@Configuration
public class AgentBulkheadConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentLlmExecutor() {
        return new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentRagExecutor() {
        return new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
