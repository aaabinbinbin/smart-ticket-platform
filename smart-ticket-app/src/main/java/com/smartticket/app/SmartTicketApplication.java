package com.smartticket.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 企业智能工单协同平台启动类。
 *
 * <p>app 模块是模块化单体的唯一启动入口，负责装配 api、auth、biz、agent、rag、infra、domain 等内部模块。</p>
 */
@MapperScan("com.smartticket.domain.mapper")
@SpringBootApplication(scanBasePackages = "com.smartticket")
public class SmartTicketApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartTicketApplication.class, args);
    }
}
