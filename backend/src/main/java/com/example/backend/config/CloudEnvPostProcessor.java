package com.example.backend.config;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 微信云托管环境变量适配:平台注入的 MYSQL_ADDRESS 是 "host:port" 一体格式,
 * Spring 占位符无法拆分,这里在配置解析前拆为 MYSQL_HOST / MYSQL_PORT。
 *
 * 激活条件(cloud profile 下 fail-fast):
 * - 有 MYSQL_ADDRESS → 拆分注入
 * - 无 MYSQL_ADDRESS 且已激活 cloud profile → 明确报错(而非静默连 127.0.0.1)
 */
public class CloudEnvPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String address = environment.getProperty("MYSQL_ADDRESS");
        boolean cloudActive = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("cloud"));
        if (address == null || address.isBlank()) {
            if (cloudActive) {
                throw new IllegalStateException(
                        "cloud profile 已激活但缺少 MYSQL_ADDRESS 环境变量"
                                + "(请确认云托管 MySQL 与服务在同一环境,或本地联调去掉 --spring.profiles.active=cloud)"
                );
            }
            return;
        }
        String[] parts = address.trim().split(":");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalStateException("MYSQL_ADDRESS 格式应为 host:port,实际: " + address);
        }
        environment.getPropertySources().addFirst(new MapPropertySource(
                "cloudMysql",
                Map.of("MYSQL_HOST", parts[0].trim(), "MYSQL_PORT", parts[1].trim())
        ));
    }

    @Override
    public int getOrder() {
        // 需在数据源配置绑定之前执行
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
