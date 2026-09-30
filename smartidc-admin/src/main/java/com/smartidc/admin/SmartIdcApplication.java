package com.smartidc.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智维云 (SmartIDC) 核心平台主启动入口
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.smartidc")
@MapperScan("com.smartidc.biz.mapper")
public class SmartIdcApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartIdcApplication.class, args);
        System.out.println("""
                \n----------------------------------------------------------
                \t智维云 (SmartIDC) 平台启动成功!
                \tKnife4j 接口文档: \thttp://localhost:8080/doc.html
                \t动环监控大屏接口: \thttp://localhost:8080/api/v1/rack/list
                ----------------------------------------------------------
                """);
    }
}
