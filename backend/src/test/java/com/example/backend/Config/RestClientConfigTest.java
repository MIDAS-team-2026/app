package com.example.backend.Config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * AI 서버가 응답하지 않을 때 호출이 무한정 기다리지 않고 제한 시간 뒤에 끊기는지 검증한다.
 */
class RestClientConfigTest {

    @Test
    @DisplayName("응답하지 않는 서버를 호출하면 응답 대기 제한 시간 뒤에 실패한다")
    void callToUnresponsiveServerFailsAfterReadTimeout() throws Exception {
        RestTemplate restTemplate = new RestClientConfig().aiRestTemplate(1000, 300);

        // 연결은 받아 주지만 응답은 보내지 않는 서버
        List<Socket> accepted = new ArrayList<>();
        try (ServerSocket silentServer = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try {
                    while (true) {
                        accepted.add(silentServer.accept());
                    }
                } catch (Exception ignored) {
                    // 테스트가 끝나 서버 소켓이 닫히면 종료
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            String url = "http://localhost:" + silentServer.getLocalPort() + "/process";

            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThrows(ResourceAccessException.class,
                            () -> restTemplate.postForObject(url, "{}", String.class)));
        } finally {
            for (Socket socket : accepted) {
                socket.close();
            }
        }
    }
}
