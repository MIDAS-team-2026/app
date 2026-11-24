package com.example.backend.Exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 모든 API의 오류 응답을 { code, message, data } 형식으로 통일한다.
 *
 * ResponseEntityExceptionHandler 를 상속해, Spring MVC 가 던지는 표준 예외
 * (없는 주소 404, 잘못된 JSON·파라미터 400, 허용되지 않은 메서드 405 등)가
 * 원래의 상태 코드를 유지한 채 같은 형식으로 내려가도록 한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String INTERNAL_ERROR_MESSAGE = "서버 내부 오류가 발생했습니다.";

    // 서비스 계층에서 상태 코드를 지정해 던진 예외 (401, 403, 404 등)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(errorBody(e.getStatusCode().value(), e.getReason()));
    }

    // 잘못된 값이 들어왔을 때 -> 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(400, e.getMessage()));
    }

    // 그 외 예상하지 못한 예외 -> 500
    // 원인은 서버 로그에만 남기고, 응답에는 내부 예외 메시지를 싣지 않는다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception e, HttpServletRequest request) {
        log.error("처리되지 않은 예외: {} {}", request.getMethod(), request.getRequestURI(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(500, INTERNAL_ERROR_MESSAGE));
    }

    // Spring MVC 표준 예외는 부모 클래스가 상태 코드를 정해 이 메서드로 넘긴다.
    // 응답 본문만 공통 형식으로 바꿔 준다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        if (statusCode.is5xxServerError()) {
            log.error("요청 처리 중 서버 오류: {}", request.getDescription(false), ex);
        }

        return super.handleExceptionInternal(
                ex, errorBody(statusCode.value(), resolveMessage(ex, statusCode)), headers, statusCode, request);
    }

    private String resolveMessage(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            return "필수 파라미터가 누락되었습니다: " + missing.getParameterName();
        }
        if (ex instanceof TypeMismatchException mismatch) {
            String name = mismatch.getPropertyName();
            return name != null
                    ? "파라미터 형식이 올바르지 않습니다: " + name
                    : "파라미터 형식이 올바르지 않습니다.";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "요청 본문(JSON) 형식이 올바르지 않습니다.";
        }

        return switch (statusCode.value()) {
            case 400 -> "요청 형식이 올바르지 않습니다.";
            case 404 -> "요청한 주소를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 요청 방식입니다.";
            case 413 -> "업로드 가능한 파일 크기를 초과했습니다.";
            case 415 -> "지원하지 않는 요청 형식입니다.";
            default -> statusCode.is5xxServerError() ? INTERNAL_ERROR_MESSAGE : "요청을 처리할 수 없습니다.";
        };
    }

    private Map<String, Object> errorBody(int code, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        response.put("data", null);
        return response;
    }
}
