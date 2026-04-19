package com.smartticket.rag.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * RAG 异步任务配置。
 *
 * <p>第一版使用 Spring 异步线程池承载关闭工单后的知识构建与向量化，不引入复杂 MQ。</p>
 */
@EnableAsync
@Configuration
public class RagAsyncConfig {
    /**
     * 知识构建和向量化专用线程池。
     */
    @Bean("knowledgeAsyncExecutor")
    public Executor knowledgeAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("knowledge-async-");
        executor.initialize();
        return executor;
    }
}
