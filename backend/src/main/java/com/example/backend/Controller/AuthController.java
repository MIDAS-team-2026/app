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
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        // 예측할 수 없는 난수(SecureRandom)로 6자리 인증번호 생성
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
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

    // 회원가입
    // 응답에는 화면에 필요한 정보만 담는다. (User 엔티티를 그대로 내보내면 비밀번호 해시까지 노출됨)
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponseDTO>> signup(@RequestBody SignupDTO signupDTO) {
        try {
            User user = new User();
            user.setPhone(signupDTO.getPhone());
            user.setPassword(signupDTO.getPassword());
            user.setName(signupDTO.getName());
            user.setRole(signupDTO.getRole());
            user.setAgeGroup(signupDTO.getAgeGroup());
            user.setGender(signupDTO.getGender());

            User savedUser = userService.register(user);

            // 가입에 쓴 인증 완료 상태는 여기서 끝낸다. (남겨두면 비밀번호 재설정에 재사용될 수 있음)
            VerificationStore.clearVerified(savedUser.getPhone());

            UserResponseDTO response = new UserResponseDTO(
                    savedUser.getId(),
                    savedUser.getPhone(),
                    savedUser.getName(),
                    savedUser.getRole(),
                    savedUser.getPatientCode()
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (ResponseStatusException e) {
            // 비밀번호 규칙 위반(400) 등은 공통 예외 처리기로 그대로 넘긴다.
            throw e;
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

    // 비밀번호 변경
    // 본인 확인이 된 요청만 허용한다.
    //  1) 비밀번호 찾기: 인증번호 확인(/verify-code)을 통과한 전화번호
    //  2) 프로필에서 변경: 로그인 토큰의 주인이 해당 전화번호의 사용자
    // 둘 다 아니면 403 (401로 주면 앱이 강제 로그아웃 처리하므로 403 사용)
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<?>> resetPassword(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody ResetPasswordDTO dto) {

        boolean verifiedByCode = VerificationStore.isVerified(dto.getPhone());
        boolean verifiedByToken = userService.isTokenOwner(authorizationHeader, dto.getPhone());

        if (!verifiedByCode && !verifiedByToken) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.fail(403, "본인 확인이 필요합니다. 인증번호 확인 후 다시 시도해주세요."));
        }

        try {
            userService.resetPassword(dto.getPhone(), dto.getNewPassword());

            // 인증 완료 상태는 한 번만 쓸 수 있다.
            VerificationStore.clearVerified(dto.getPhone());
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (ResponseStatusException e) {
            // 비밀번호 규칙 위반(400) 등은 공통 예외 처리기로 그대로 넘긴다.
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "비밀번호 변경에 실패했습니다."));
        }
    }

    // 회원 탈퇴
    // 로그인한 본인만 자신의 계정을 탈퇴할 수 있다. (토큰 없음 401, 다른 사람 번호 403)
    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<?>> withdraw(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody ForgotPasswordDTO dto) {

        User requester = userService.getAuthenticatedUser(authorizationHeader);

        if (!requester.getPhone().equals(dto.getPhone())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.fail(403, "본인 계정만 탈퇴할 수 있습니다."));
        }

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
    // 환자 본인 또는 그 환자와 연동된 보호자만 조회할 수 있다.
    @GetMapping("/patients/{patientId}/guardians")
    public ResponseEntity<ApiResponse<List<UserResponseDTO>>> getGuardiansByPatient(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer patientId) {

        userService.validatePatientAccess(authorizationHeader, patientId);

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
    // 보호자 본인만 자신의 환자 목록을 조회할 수 있다.
    @GetMapping("/protectors/{protectorId}/patients")
    public ResponseEntity<ApiResponse<List<UserResponseDTO>>> getPatientsByProtector(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer protectorId) {

        userService.validateSelfAccess(authorizationHeader, protectorId);

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


    // 코드로 환자 정보 조회 (교차검증용 — 이름·전화번호만 응답)
    // 로그인한 사용자만 조회할 수 있다.
    @GetMapping("/patients/by-code/{code}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getPatientByCode(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String code) {

        userService.getAuthenticatedUser(authorizationHeader);

        try {
            User patient = userService.findByPatientCode(code.toUpperCase());
            UserResponseDTO dto = new UserResponseDTO(
                    patient.getId(),
                    patient.getPhone(),
                    patient.getName(),
                    patient.getRole(),
                    null   // patientCode 노출 안 함
            );
            return ResponseEntity.ok(ApiResponse.success(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(404, e.getMessage()));
        }
    }

    // 보호자-환자 연동 해제
    // 해당 보호자 본인 또는 해당 환자 본인만 해제할 수 있다.
    @DeleteMapping("/protectors/{protectorId}/patients/{patientId}")
    public ResponseEntity<ApiResponse<Void>> unlinkPatient(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer protectorId,
            @PathVariable Integer patientId) {

        userService.validateLinkAccess(authorizationHeader, protectorId, patientId);

        try {
            userService.unlinkPatient(protectorId, patientId);
            return ResponseEntity.ok(ApiResponse.success());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.fail(400, e.getMessage()));
        }
    }

    // 보호자-환자 연동
    // 보호자 본인만 자신의 계정에 환자를 연동할 수 있다.
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<Void>> linkProtector(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody LinkRequestDTO linkDTO) {

        userService.validateSelfAccess(authorizationHeader, linkDTO.getProtectorId());

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