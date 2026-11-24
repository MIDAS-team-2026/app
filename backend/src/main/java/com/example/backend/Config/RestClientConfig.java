package com.example.backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Python AI 서버를 호출할 때 쓰는 RestTemplate.
 *
 * 기본 RestTemplate(new RestTemplate())은 응답을 무한정 기다린다.
 * AI 서버가 응답하지 않으면 요청 스레드와 DB 연결이 계속 붙잡혀 백엔드 전체가 멈출 수 있으므로
 * 연결·응답 대기 시간에 제한을 둔다.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate aiRestTemplate(
            @Value("${ai.python.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.python.read-timeout-ms:60000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        return new RestTemplate(factory);
    }
}
