package com.example.backend.Controller;

import com.example.backend.Model.DTO.*;
import com.example.backend.Model.DTO.auth.*;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Service.UserService;
import com.example.backend.Util.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<User>> signup(@RequestBody SignupDTO signupDTO) {
        try {
            User user = new User();
            user.setPhone(signupDTO.getPhone());
            user.setPassword(signupDTO.getPassword());
            user.setName(signupDTO.getName());
            user.setRole(signupDTO.getRole());
            user.setAgeGroup(signupDTO.getAgeGroup());
            user.setGender(signupDTO.getGender());

            User savedUser = userService.register(user);
            return ResponseEntity.ok(ApiResponse.success(savedUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail(409, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "회원가입 실패"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginDTO loginDTO) {
        User user = userService.login(loginDTO.getPhone(), loginDTO.getPassword());

        if (user != null) {

            String token = jwtTokenProvider.createToken(user.getPhone(), user.getRole());

            LoginResponse loginDTO1 = new LoginResponse(
                    user.getPhone(),
                    user.getName(),
                    token,
                    user.getRole(),
                    user.getId()
            );
            return ResponseEntity.ok(ApiResponse.success(loginDTO1));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(401, "아이디 또는 비밀번호가 틀렸습니다."));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<?>> forgotPassword(@RequestBody ForgotPasswordDTO dto) {
        if (!userService.existsByPhone(dto.getPhone())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(404, "등록되지 않은 전화번호입니다."));
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<?>> resetPassword(@RequestBody ResetPasswordDTO dto) {
        try {
            userService.resetPassword(dto.getPhone(), dto.getNewPassword());
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "비밀번호 변경에 실패했습니다."));
        }
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<?>> withdraw(@RequestBody ForgotPasswordDTO dto) {
        try {
            userService.deleteUser(dto.getPhone());
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "탈퇴 처리 중 오류가 발생했습니다."));
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "연동 처리 중 서버 오류가 발생했습니다."));
        }
    }
}