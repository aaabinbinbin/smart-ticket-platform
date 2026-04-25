package com.smartticket.rag.config;

import com.smartticket.infra.ai.VectorStoreConfig.SpringAiVectorStoreHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 主路径与 fallback 路径日志。
 */
@Configuration(proxyBeanMethods = false)
public class RagPathLogger {
    private static final Logger log = LoggerFactory.getLogger(RagPathLogger.class);

    /**
     * 创建路径日志启动器。
     */
    @Bean
    public ApplicationRunner ragPathApplicationRunner(
            @Value("${smart-ticket.ai.vector-store.enabled:false}") boolean vectorStoreEnabled,
            @Value("${smart-ticket.ai.embedding.enabled:false}") boolean embeddingEnabled,
            @Value("${smart-ticket.ai.pgvector.url:}") String pgvectorUrl,
            ObjectProvider<SpringAiVectorStoreHolder> vectorStoreHolderProvider
    ) {
        return args -> {
            SpringAiVectorStoreHolder holder = vectorStoreHolderProvider.getIfAvailable();
            boolean vectorStoreReady = holder != null && holder.vectorStore() != null;
            String retrievalPath = vectorStoreEnabled && vectorStoreReady ? "PGVECTOR" : "MYSQL_FALLBACK";
            log.info("RAG 路径状态：embeddingEnabled={}, vectorStoreEnabled={}, vectorStoreReady={}, retrievalPath={}, pgvectorUrl={}",
                    embeddingEnabled, vectorStoreEnabled, vectorStoreReady, retrievalPath, pgvectorUrl);
            if (!vectorStoreEnabled || !vectorStoreReady) {
                log.warn("RAG MySQL 回退路径已启用。该路径仅用于开发阶段回退，不应作为长期主检索路径。");
            }
        };
    }
}
