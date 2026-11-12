package com.yunshu.zhixue.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("云枢智学 API")
                .version("v1")
                .description("学员端 /api/portal/v1 ，管理端 /api/admin/v1"));
    }
}
