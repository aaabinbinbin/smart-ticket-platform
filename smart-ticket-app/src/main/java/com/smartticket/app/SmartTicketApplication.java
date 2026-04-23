package com.smartticket.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.smartticket.domain.mapper")
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = "com.smartticket",
        excludeName = "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
)
public class SmartTicketApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartTicketApplication.class, args);
    }
}
