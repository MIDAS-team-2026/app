package com.example.backend.Controller;

import com.example.backend.Exception.GlobalExceptionHandler;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import com.example.backend.Model.Repository.UserRepository;
import com.example.backend.Service.UserService;
import com.example.backend.Util.JwtTokenProvider;
import com.example.backend.Util.VerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 API 의 본인 확인·접근 권한 검사를 HTTP 요청 단위로 검증한다.
 * DB 만 Mockito 로 대체하고, 서비스·JWT·BCrypt·예외 처리기는 실제 구현을 쓴다.
 * 테스트마다 서로 다른 전화번호를 써서 인증번호 저장소(static)가 서로 영향을 주지 않게 한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private RecallQuestionRepository recallQuestionRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService =
                new UserService(userRepository, recallQuestionRepository, passwordEncoder, jwtTokenProvider);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(userService, jwtTokenProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private User user(int id, String phone, String role) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setName("사용자" + id);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode("abcd1234"));
        return user;
    }

    // DB 에 이 사용자가 있는 것처럼 만들어 두고, 그 사용자의 로그인 토큰 헤더를 돌려준다.
    private String loginAs(User user) {
        when(userRepository.findByPhone(user.getPhone())).thenReturn(Optional.of(user));
        return bearer(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createToken(user.getPhone(), user.getRole());
    }

    private String json(String body) {
        return body.replace('\'', '"');
    }

    // ───────────── 회원가입 ─────────────

    @Test
    @DisplayName("회원가입 응답에 비밀번호(해시)가 포함되지 않는다")
    void signupResponseDoesNotExposePassword() throws Exception {
        when(userRepository.existsByPhone("01022220001")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10);
            return saved;
        });

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220001','password':'abcd1234','name':'홍길동','role':'PATIENT'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.phone").value("01022220001"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.role").value("PATIENT"))
                .andExpect(jsonPath("$.data.patientCode").isNotEmpty())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.patients").doesNotExist());
    }

    @Test
    @DisplayName("규칙에 맞지 않는 비밀번호로 가입하면 400")
    void signupWithWeakPasswordIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220002','password':'12345678','name':'홍길동','role':'PATIENT'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("이미 가입된 전화번호로 가입하면 409")
    void signupWithDuplicatePhoneIsConflict() throws Exception {
        when(userRepository.existsByPhone("01022220003")).thenReturn(true);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220003','password':'abcd1234','name':'홍길동','role':'PATIENT'}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("가입에 쓴 인증 완료 상태는 가입 후 사라져 비밀번호 재설정에 재사용할 수 없다")
    void verifiedStateIsClearedAfterSignup() throws Exception {
        String phone = "01022220004";
        VerificationStore.saveCode(phone, "123456");
        VerificationStore.verifyCode(phone, "123456");
        when(userRepository.existsByPhone(phone)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'" + phone + "','password':'abcd1234','name':'홍길동','role':'PROTECTOR'}")))
                .andExpect(status().isOk());

        assertFalse(VerificationStore.isVerified(phone));
    }

    // ───────────── 비밀번호 변경 ─────────────

    @Test
    @DisplayName("인증도 로그인도 없이 비밀번호를 바꾸려 하면 403 이고 비밀번호는 그대로다")
    void resetPasswordWithoutVerificationIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220005','newPassword':'hacked1234'}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(userRepository, never()).findByPhone(any());
    }

    @Test
    @DisplayName("인증번호 확인을 통과하면 비밀번호를 바꿀 수 있고, 같은 인증으로 두 번은 못 바꾼다")
    void resetPasswordAfterCodeVerificationWorksOnlyOnce() throws Exception {
        User stored = user(1, "01022220006", "PATIENT");
        when(userRepository.findByPhone(stored.getPhone())).thenReturn(Optional.of(stored));
        VerificationStore.saveCode(stored.getPhone(), "123456");

        mockMvc.perform(post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220006','code':'123456'}")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220006','newPassword':'newpass2026'}")))
                .andExpect(status().isOk());

        assertTrue(passwordEncoder.matches("newpass2026", stored.getPassword()));

        // 같은 인증 완료 상태로 한 번 더 시도 → 거부
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220006','newPassword':'another2026'}")))
                .andExpect(status().isForbidden());

        assertTrue(passwordEncoder.matches("newpass2026", stored.getPassword()));
    }

    @Test
    @DisplayName("틀린 인증번호로는 인증되지 않아 비밀번호를 바꿀 수 없다")
    void wrongCodeDoesNotAllowPasswordReset() throws Exception {
        VerificationStore.saveCode("01022220007", "123456");

        mockMvc.perform(post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220007','code':'000000'}")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220007','newPassword':'hacked1234'}")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인한 본인은 인증번호 없이도 자신의 비밀번호를 바꿀 수 있다 (프로필 화면)")
    void loggedInUserCanChangeOwnPassword() throws Exception {
        User stored = user(1, "01022220008", "PATIENT");
        String token = loginAs(stored);

        mockMvc.perform(post("/api/auth/reset-password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220008','newPassword':'newpass2026'}")))
                .andExpect(status().isOk());

        assertTrue(passwordEncoder.matches("newpass2026", stored.getPassword()));
    }

    @Test
    @DisplayName("로그인했더라도 다른 사람의 비밀번호는 바꿀 수 없다 (403)")
    void loggedInUserCannotChangeOthersPassword() throws Exception {
        User attacker = user(2, "01022220009", "PATIENT");

        mockMvc.perform(post("/api/auth/reset-password")
                        .header("Authorization", bearer(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220010','newPassword':'hacked1234'}")))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).findByPhone("01022220010");
    }

    @Test
    @DisplayName("본인 확인을 통과해도 규칙에 맞지 않는 비밀번호면 400")
    void resetPasswordWithWeakPasswordIsBadRequest() throws Exception {
        User stored = user(1, "01022220011", "PATIENT");

        mockMvc.perform(post("/api/auth/reset-password")
                        .header("Authorization", bearer(stored))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220011','newPassword':'12345678'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ───────────── 회원 탈퇴 ─────────────

    @Test
    @DisplayName("토큰 없이 탈퇴를 요청하면 401 이고 계정은 삭제되지 않는다")
    void withdrawWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220012'}")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("다른 사람의 계정은 탈퇴시킬 수 없다 (403)")
    void withdrawOfAnotherAccountIsForbidden() throws Exception {
        User attacker = user(2, "01022220013", "PATIENT");
        String token = loginAs(attacker);

        mockMvc.perform(post("/api/auth/withdraw")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220014'}")))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("로그인한 본인은 자신의 계정을 탈퇴할 수 있다")
    void userCanWithdrawOwnAccount() throws Exception {
        User stored = user(1, "01022220015", "PATIENT");
        String token = loginAs(stored);

        mockMvc.perform(post("/api/auth/withdraw")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'phone':'01022220015'}")))
                .andExpect(status().isOk());

        verify(userRepository).delete(stored);
    }

    // ───────────── 보호자·환자 조회 ─────────────

    @Test
    @DisplayName("토큰 없이 보호자의 환자 목록을 조회하면 401")
    void patientListWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/protectors/2/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다른 보호자의 환자 목록은 조회할 수 없다 (403)")
    void patientListOfAnotherProtectorIsForbidden() throws Exception {
        User protector = user(2, "01022220016", "PROTECTOR");
        String token = loginAs(protector);

        mockMvc.perform(get("/api/auth/protectors/7/patients").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("보호자 본인은 자신의 환자 목록을 조회할 수 있다")
    void protectorCanListOwnPatients() throws Exception {
        User patient = user(1, "01022220017", "PATIENT");
        patient.setPatientCode("AB12CD34");
        User protector = user(2, "01022220018", "PROTECTOR");
        protector.getPatients().add(patient);
        String token = loginAs(protector);
        when(userRepository.findById(2)).thenReturn(Optional.of(protector));

        mockMvc.perform(get("/api/auth/protectors/2/patients").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId").value(1))
                .andExpect(jsonPath("$.data[0].patientCode").value("AB12CD34"));
    }

    @Test
    @DisplayName("연동되지 않은 사람은 환자의 보호자 목록을 조회할 수 없다 (403)")
    void guardianListForUnlinkedUserIsForbidden() throws Exception {
        User patient = user(1, "01022220019", "PATIENT");
        User stranger = user(9, "01022220020", "PROTECTOR");
        String token = loginAs(stranger);
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));

        mockMvc.perform(get("/api/auth/patients/1/guardians").header("Authorization", token))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).findByPatients_Id(any());
    }

    @Test
    @DisplayName("환자 본인은 자신의 보호자 목록을 조회할 수 있다")
    void patientCanListOwnGuardians() throws Exception {
        User patient = user(1, "01022220021", "PATIENT");
        User protector = user(2, "01022220022", "PROTECTOR");
        String token = loginAs(patient);
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));
        when(userRepository.findByPatients_Id(1)).thenReturn(java.util.List.of(protector));

        mockMvc.perform(get("/api/auth/patients/1/guardians").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId").value(2));
    }

    @Test
    @DisplayName("환자 코드 조회는 로그인한 사용자만 할 수 있다")
    void patientLookupByCodeRequiresLogin() throws Exception {
        User patient = user(1, "01022220023", "PATIENT");
        User protector = user(2, "01022220024", "PROTECTOR");

        mockMvc.perform(get("/api/auth/patients/by-code/AB12CD34"))
                .andExpect(status().isUnauthorized());

        String token = loginAs(protector);
        when(userRepository.findByPatientCode("AB12CD34")).thenReturn(Optional.of(patient));

        mockMvc.perform(get("/api/auth/patients/by-code/ab12cd34").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.patientCode").doesNotExist());
    }

    // ───────────── 연동 / 연동 해제 ─────────────

    @Test
    @DisplayName("다른 보호자의 이름으로는 환자를 연동할 수 없다 (403)")
    void linkOnBehalfOfAnotherProtectorIsForbidden() throws Exception {
        User protector = user(2, "01022220025", "PROTECTOR");
        String token = loginAs(protector);

        mockMvc.perform(post("/api/auth/link")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'protectorId':7,'patientCode':'AB12CD34'}")))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).findByPatientCode(any());
    }

    @Test
    @DisplayName("보호자 본인은 환자 코드로 환자를 연동할 수 있다")
    void protectorCanLinkPatient() throws Exception {
        User patient = user(1, "01022220026", "PATIENT");
        User protector = user(2, "01022220027", "PROTECTOR");
        String token = loginAs(protector);
        when(userRepository.findByPatientCode("AB12CD34")).thenReturn(Optional.of(patient));
        when(userRepository.findById(2)).thenReturn(Optional.of(protector));

        mockMvc.perform(post("/api/auth/link")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'protectorId':2,'patientCode':'AB12CD34'}")))
                .andExpect(status().isOk());

        assertTrue(protector.getPatients().contains(patient));
    }

    @Test
    @DisplayName("관계없는 사람은 다른 사람의 연동을 해제할 수 없다 (403)")
    void unlinkByStrangerIsForbidden() throws Exception {
        User stranger = user(9, "01022220028", "PROTECTOR");
        String token = loginAs(stranger);

        mockMvc.perform(delete("/api/auth/protectors/2/patients/1").header("Authorization", token))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("보호자 본인은 자신의 환자 연동을 해제할 수 있다")
    void protectorCanUnlinkOwnPatient() throws Exception {
        User patient = user(1, "01022220029", "PATIENT");
        User protector = user(2, "01022220030", "PROTECTOR");
        protector.getPatients().add(patient);
        String token = loginAs(protector);
        when(userRepository.findById(2)).thenReturn(Optional.of(protector));

        mockMvc.perform(delete("/api/auth/protectors/2/patients/1").header("Authorization", token))
                .andExpect(status().isOk());

        assertFalse(protector.getPatients().contains(patient));
    }

    @Test
    @DisplayName("토큰 없이 연동 해제를 요청하면 401")
    void unlinkWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/auth/protectors/2/patients/1"))
                .andExpect(status().isUnauthorized());
    }
}
