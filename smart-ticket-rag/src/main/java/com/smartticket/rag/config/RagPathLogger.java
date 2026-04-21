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

    @Bean
    public ApplicationRunner ragPathApplicationRunner(
            @Value("${smart-ticket.ai.vector-store.enabled:false}") boolean vectorStoreEnabled,
            @Value("${smart-ticket.ai.embedding.enabled:false}") boolean embeddingEnabled,
            @Value("${smart-ticket.ai.pgvector.url:}") String pgvectorUrl,
            ObjectProvider<SpringAiVectorStoreHolder> vectorStoreHolderProvider
    ) {
        return args -> {
            boolean holderReady = vectorStoreHolderProvider.getIfAvailable() != null;
            log.info("rag path status: embeddingEnabled={}, vectorStoreEnabled={}, vectorStoreReady={}, pgvectorUrl={}",
                    embeddingEnabled, vectorStoreEnabled, holderReady, pgvectorUrl);
            if (!vectorStoreEnabled || !holderReady) {
                log.warn("rag mysql fallback is active. this path is for development fallback and should not be the long-term main retrieval path.");
            }
        };
    }
}
