package com.example.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    /**
     * Boot 4.1 模块化后 webmvc starter 不再自动注册 ObjectMapper Bean,
     * 模板馆引擎(卡片 payload 序列化/AI 网关 JSON)显式声明单例共用。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
