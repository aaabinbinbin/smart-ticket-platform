package com.smartticket.agent.resilience;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 慢资源隔离配置。
 *
 * <p>P6 阶段先为 LLM 与 RAG 预留独立线程池 Bean，避免后续把慢模型调用和核心工单链路混在同一执行资源中。
 * 当前同步 `/api/agent/chat` 主链不改变协议，也不强制异步化写操作；该配置本身不执行写操作，
 * 不会修改 session、memory、pendingAction 或 trace。</p>
 */
@Configuration
public class AgentBulkheadConfig {

    /**
     * LLM 调用隔离线程池。
     *
     * @return 面向模型调用的固定大小线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentLlmExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    /**
     * RAG 调用隔离线程池。
     *
     * @return 面向历史案例检索的固定大小线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentRagExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
