package com.example.backend.Controller;

import com.example.backend.Model.DTO.*;
import com.example.backend.Model.DTO.auth.*;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Service.UserService;
import com.example.backend.Model.DTO.auth.SendCodeDTO;
import com.example.backend.Util.JwtTokenProvider;
import com.example.backend.Util.VerificationStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 인증번호 발송 (SMS 미연동 — 서버 로그에 6자리 코드 출력)
     * purpose: SIGNUP(중복 불가) / FORGOT_PASSWORD(존재해야 함)
     */
    @PostMapping("/send-code")
    public ResponseEntity<ApiResponse<?>> sendCode(@RequestBody SendCodeDTO dto) {
        if ("SIGNUP".equalsIgnoreCase(dto.getPurpose())) {
            if (userService.existsByPhone(dto.getPhone())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.fail(409, "이미 가입된 전화번호입니다."));
            }
        } else if ("FORGOT_PASSWORD".equalsIgnoreCase(dto.getPurpose())
                || "WITHDRAW".equalsIgnoreCase(dto.getPurpose())) {
            if (!userService.existsByPhone(dto.getPhone())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.fail(404, "등록되지 않은 전화번호입니다."));
            }
        }
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        VerificationStore.saveCode(dto.getPhone(), code);
        log.info("[인증코드] 전화번호: {} → 코드: {}", dto.getPhone(), code);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 인증번호 검증
     */
    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<?>> verifyCode(@RequestBody VerifyCodeDTO dto) {
        if (VerificationStore.verifyCode(dto.getPhone(), dto.getCode())) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, "인증번호가 올바르지 않습니다."));
    }

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

            String patientCode = null;

            if ("PATIENT".equalsIgnoreCase(user.getRole())) {
                patientCode = user.getPatientCode();
            }

            LoginResponse response = new LoginResponse(
                    user.getPhone(),
                    user.getName(),
                    token,
                    user.getRole(),
                    user.getId(),
                    patientCode
            );

            return ResponseEntity.ok(ApiResponse.success(response));
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

    // 환자 기준: 해당 환자에게 연결된 보호자 목록 조회
    @GetMapping("/patients/{patientId}/guardians")
    public ResponseEntity<ApiResponse<List<UserResponseDTO>>> getGuardiansByPatient(@PathVariable Integer patientId) {
        List<UserResponseDTO> guardians = userService.getGuardiansByPatient(patientId).stream()
                .map(u -> new UserResponseDTO(
                        u.getId(),
                        u.getPhone(),
                        u.getName(),
                        u.getRole(),
                        null
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(guardians));
    }

    // 보호자 기준: 해당 보호자가 연결한 환자 목록 조회
    @GetMapping("/protectors/{protectorId}/patients")
    public ResponseEntity<ApiResponse<List<UserResponseDTO>>> getPatientsByProtector(
            @PathVariable Integer protectorId) {

        try {
            List<UserResponseDTO> patients = userService.getPatientsByProtector(protectorId).stream()
                    .map(u -> new UserResponseDTO(
                            u.getId(),
                            u.getPhone(),
                            u.getName(),
                            u.getRole(),
                            u.getPatientCode()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(patients));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(404, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.fail(400, e.getMessage()));
        }
    }


    @PostMapping("/link")
    public ResponseEntity<ApiResponse<Void>> linkProtector(@RequestBody LinkRequestDTO linkDTO) {
        try {
            userService.linkProtector(linkDTO.getProtectorId(), linkDTO.getPatientCode());
            return ResponseEntity.ok(ApiResponse.success());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.fail(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "연동 처리 중 서버 오류가 발생했습니다."));
        }
    }
}