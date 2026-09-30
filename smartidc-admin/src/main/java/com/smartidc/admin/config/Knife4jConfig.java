package com.smartidc.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 3.0 接口文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智维云 (SmartIDC) 智能运维平台 API 文档")
                        .version("v1.0.0")
                        .description("面向数据中心动环监控、机架空间资产全生命周期与 AIOps 智能运维协同中台")
                        .contact(new Contact().name("SmartIDC 架构研发组"))
                        .license(new License().name("Apache 2.0")));
    }
}
