package com.example.backend.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 서버의 모든 API 주소에 대하여
                .allowedOrigins("*") // 안드로이드 등 모든 프론트엔드 환경의 접근을 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 허용할 행동들
                .allowedHeaders("*"); // 모든 데이터 헤더 허용
    }
}