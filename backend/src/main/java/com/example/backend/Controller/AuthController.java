package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.LinkRequestDTO;
import com.example.backend.Model.DTO.LoginDTO;
import com.example.backend.Model.DTO.SignupDTO;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<User>> signup(@RequestBody SignupDTO signupDTO) {
        try {
            User user = new User();
            user.setEmail(signupDTO.getEmail());
            user.setPassword(signupDTO.getPassword());
            user.setName(signupDTO.getName());
            user.setRole(signupDTO.getRole());
            user.setAgeGroup(signupDTO.getAgeGroup());
            user.setGender(signupDTO.getGender());

            User savedUser = userService.register(user);
            return ResponseEntity.ok(ApiResponse.success(savedUser));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "회원가입 실패"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginDTO loginDTO) {
        User user = userService.login(loginDTO.getEmail(), loginDTO.getPassword());

        if (user != null) {
            return ResponseEntity.ok(ApiResponse.success(user));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(401, "아이디 또는 비밀번호가 틀렸습니다."));
        }
    }

    @PostMapping("/link")
    public ResponseEntity<ApiResponse<Void>> linkProtector(@RequestBody LinkRequestDTO linkDTO) {
        try {
            userService.linkProtector(linkDTO.getProtectorId(), linkDTO.getPatientCode());
            // 성공 시 별도의 데이터 없이 성공 메시지만 반환
            return ResponseEntity.ok(ApiResponse.success());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 잘못된 코드거나 권한이 없는 경우
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.fail(400, e.getMessage()));
        } catch (Exception e) {
            // 기타 서버 에러
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "연동 처리 중 서버 오류가 발생했습니다."));
        }
    }
}