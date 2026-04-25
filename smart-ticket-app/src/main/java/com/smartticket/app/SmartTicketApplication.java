package com.smartticket.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.smartticket.domain.mapper")
@EnableScheduling
@ConfigurationPropertiesScan("com.smartticket")
@SpringBootApplication(
        scanBasePackages = "com.smartticket",
        excludeName = "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
)
/**
 * Smart工单Application类。
 */
public class SmartTicketApplication {
    /**
     * 应用启动入口。
     */
    public static void main(String[] args) {
        SpringApplication.run(SmartTicketApplication.class, args);
    }
}
