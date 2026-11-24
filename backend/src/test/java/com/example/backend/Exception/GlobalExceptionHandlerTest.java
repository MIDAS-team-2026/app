package com.example.backend.Exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 예외 처리기가 예외 종류별로 올바른 상태 코드와
 * { code, message, data } 형식의 본문을 내려주는지 검증한다.
 * DB 나 외부 서비스 없이 실행된다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    // 예외를 일부러 발생시키는 테스트 전용 컨트롤러
    @RestController
    static class StubController {

        @GetMapping("/stub/illegal-argument")
        public String illegalArgument() {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }

        @GetMapping("/stub/forbidden")
        public String forbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 사용자 정보에 접근할 권한이 없습니다.");
        }

        @GetMapping("/stub/unexpected")
        public String unexpected() {
            throw new IllegalStateException("jdbc:mysql://localhost:3306/midas 연결 실패");
        }

        @GetMapping("/stub/param")
        public String param(@RequestParam Integer userId) {
            return "ok";
        }

        @PostMapping("/stub/body")
        public String body(@RequestBody Map<String, Object> body) {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void illegalArgumentIsReturnedAs400WithItsMessage() throws Exception {
        mockMvc.perform(get("/stub/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void responseStatusExceptionKeepsItsStatusAndReason() throws Exception {
        mockMvc.perform(get("/stub/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("해당 사용자 정보에 접근할 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void unexpectedExceptionIsReturnedAs500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/stub/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE))
                // 내부 예외 메시지(DB 주소 등)가 응답에 노출되지 않아야 한다
                .andExpect(jsonPath("$.message").value(not(containsString("jdbc"))));
    }

    @Test
    void missingRequiredParameterIsReturnedAs400() throws Exception {
        mockMvc.perform(get("/stub/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("userId")));
    }

    @Test
    void wrongParameterTypeIsReturnedAs400() throws Exception {
        mockMvc.perform(get("/stub/param").param("userId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void malformedJsonBodyIsReturnedAs400() throws Exception {
        mockMvc.perform(post("/stub/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("요청 본문(JSON) 형식이 올바르지 않습니다."));
    }

    @Test
    void unsupportedHttpMethodIsReturnedAs405() throws Exception {
        mockMvc.perform(post("/stub/param"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void unknownUrlIsReturnedAs404NotAs500() throws Exception {
        mockMvc.perform(get("/stub/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("요청한 주소를 찾을 수 없습니다."));
    }
}
