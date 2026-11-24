package com.example.backend.Service;

import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import com.example.backend.Model.Repository.UserRepository;
import com.example.backend.Util.JwtTokenProvider;
import com.example.backend.Util.PasswordPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회원가입·로그인·비밀번호 변경과 접근 권한 검사 로직 검증.
 * DB 는 Mockito 로 대체하고, 비밀번호 암호화(BCrypt)와 JWT 는 실제 구현을 그대로 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RecallQuestionRepository recallQuestionRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, recallQuestionRepository, passwordEncoder, jwtTokenProvider);
    }

    private User user(int id, String phone, String role) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setName("사용자" + id);
        user.setRole(role);
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createToken(user.getPhone(), user.getRole());
    }

    private ResponseStatusException assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, e.getStatusCode());
        return e;
    }

    // ───────────── 토큰으로 요청자 확인 ─────────────

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void missingAuthorizationHeaderIsUnauthorized() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> userService.getAuthenticatedUser(null));
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 401")
    void headerWithoutBearerPrefixIsUnauthorized() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> userService.getAuthenticatedUser("Basic abc"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 401")
    void invalidTokenIsUnauthorized() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> userService.getAuthenticatedUser("Bearer not-a-jwt"));
        // 가입 직후처럼 토큰이 빈 문자열로 저장된 경우
        assertStatus(HttpStatus.UNAUTHORIZED, () -> userService.getAuthenticatedUser("Bearer "));
    }

    @Test
    @DisplayName("토큰은 유효하지만 사용자가 이미 탈퇴했으면 401")
    void tokenOfDeletedUserIsUnauthorized() {
        User deleted = user(1, "01011110001", "PATIENT");
        when(userRepository.findByPhone(deleted.getPhone())).thenReturn(Optional.empty());

        assertStatus(HttpStatus.UNAUTHORIZED, () -> userService.getAuthenticatedUser(bearer(deleted)));
    }

    @Test
    @DisplayName("유효한 토큰이면 토큰의 주인을 돌려준다")
    void validTokenReturnsItsOwner() {
        User patient = user(1, "01011110002", "PATIENT");
        when(userRepository.findByPhone(patient.getPhone())).thenReturn(Optional.of(patient));

        assertSame(patient, userService.getAuthenticatedUser(bearer(patient)));
    }

    // ───────────── 환자 정보 접근 권한 ─────────────

    @Test
    @DisplayName("본인 정보에는 접근할 수 있다")
    void patientCanAccessOwnData() {
        User patient = user(1, "01011110003", "PATIENT");
        when(userRepository.findByPhone(patient.getPhone())).thenReturn(Optional.of(patient));
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));

        assertSame(patient, userService.validatePatientAccess(bearer(patient), 1));
    }

    @Test
    @DisplayName("연동된 보호자는 환자 정보에 접근할 수 있다")
    void linkedProtectorCanAccessPatientData() {
        User patient = user(1, "01011110004", "PATIENT");
        User protector = user(2, "01011110005", "PROTECTOR");
        protector.getPatients().add(patient);
        when(userRepository.findByPhone(protector.getPhone())).thenReturn(Optional.of(protector));
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));

        assertSame(protector, userService.validatePatientAccess(bearer(protector), 1));
    }

    @Test
    @DisplayName("연동되지 않은 보호자는 403")
    void unlinkedProtectorIsForbidden() {
        User patient = user(1, "01011110006", "PATIENT");
        User protector = user(2, "01011110007", "PROTECTOR");
        when(userRepository.findByPhone(protector.getPhone())).thenReturn(Optional.of(protector));
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));

        assertStatus(HttpStatus.FORBIDDEN, () -> userService.validatePatientAccess(bearer(protector), 1));
    }

    @Test
    @DisplayName("다른 환자의 정보는 403")
    void otherPatientIsForbidden() {
        User patient = user(1, "01011110008", "PATIENT");
        User other = user(3, "01011110009", "PATIENT");
        when(userRepository.findByPhone(other.getPhone())).thenReturn(Optional.of(other));
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));

        assertStatus(HttpStatus.FORBIDDEN, () -> userService.validatePatientAccess(bearer(other), 1));
    }

    @Test
    @DisplayName("대상 사용자가 없으면 404")
    void missingTargetUserIsNotFound() {
        User patient = user(1, "01011110010", "PATIENT");
        when(userRepository.findByPhone(patient.getPhone())).thenReturn(Optional.of(patient));
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> userService.validatePatientAccess(bearer(patient), 999));
    }

    @Test
    @DisplayName("토큰 없이 환자 정보에 접근하면 401")
    void patientAccessWithoutTokenIsUnauthorized() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> userService.validatePatientAccess(null, 1));
    }

    // ───────────── 본인 확인 / 연동 해제 권한 ─────────────

    @Test
    @DisplayName("본인 ID 로 요청하면 통과, 다른 사람 ID 면 403")
    void selfAccessAllowsOnlyOwnId() {
        User protector = user(2, "01011110011", "PROTECTOR");
        when(userRepository.findByPhone(protector.getPhone())).thenReturn(Optional.of(protector));

        assertSame(protector, userService.validateSelfAccess(bearer(protector), 2));
        assertStatus(HttpStatus.FORBIDDEN, () -> userService.validateSelfAccess(bearer(protector), 5));
        assertStatus(HttpStatus.FORBIDDEN, () -> userService.validateSelfAccess(bearer(protector), null));
    }

    @Test
    @DisplayName("연동 해제는 해당 보호자 또는 해당 환자만 가능하다")
    void linkAccessAllowsOnlyTheProtectorOrThePatient() {
        User protector = user(2, "01011110012", "PROTECTOR");
        User patient = user(1, "01011110013", "PATIENT");
        User stranger = user(9, "01011110014", "PROTECTOR");
        when(userRepository.findByPhone(protector.getPhone())).thenReturn(Optional.of(protector));
        when(userRepository.findByPhone(patient.getPhone())).thenReturn(Optional.of(patient));
        when(userRepository.findByPhone(stranger.getPhone())).thenReturn(Optional.of(stranger));

        assertSame(protector, userService.validateLinkAccess(bearer(protector), 2, 1));
        assertSame(patient, userService.validateLinkAccess(bearer(patient), 2, 1));
        assertStatus(HttpStatus.FORBIDDEN, () -> userService.validateLinkAccess(bearer(stranger), 2, 1));
    }

    @Test
    @DisplayName("토큰의 주인이 해당 전화번호의 사용자인지 알려준다")
    void isTokenOwnerComparesTokenPhoneWithGivenPhone() {
        User patient = user(1, "01011110015", "PATIENT");

        assertTrue(userService.isTokenOwner(bearer(patient), "01011110015"));
        assertFalse(userService.isTokenOwner(bearer(patient), "01099999999"));
        assertFalse(userService.isTokenOwner(null, "01011110015"));
        assertFalse(userService.isTokenOwner("Bearer not-a-jwt", "01011110015"));
        assertFalse(userService.isTokenOwner("Bearer ", "01011110015"));
        assertFalse(userService.isTokenOwner(bearer(patient), null));
    }

    // ───────────── 회원가입 ─────────────

    private User signupRequest(String phone, String password, String role) {
        User dto = new User();
        dto.setPhone(phone);
        dto.setPassword(password);
        dto.setName("홍길동");
        dto.setRole(role);
        return dto;
    }

    @Test
    @DisplayName("비밀번호는 평문이 아닌 BCrypt 해시로 저장된다")
    void registerStoresBcryptHashInsteadOfPlainPassword() {
        when(userRepository.existsByPhone("01011110016")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = userService.register(signupRequest("01011110016", "abcd1234", "PROTECTOR"));

        assertNotEquals("abcd1234", saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2"));
        assertTrue(passwordEncoder.matches("abcd1234", saved.getPassword()));
    }

    @Test
    @DisplayName("환자로 가입하면 8자리 연동 코드와 기본 회상 질문이 만들어진다")
    void registeringPatientIssuesCodeAndDefaultQuestions() {
        when(userRepository.existsByPhone("01011110017")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = userService.register(signupRequest("01011110017", "abcd1234", "PATIENT"));

        assertNotNull(saved.getPatientCode());
        assertEquals(8, saved.getPatientCode().length());
        verify(recallQuestionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("보호자로 가입하면 연동 코드가 없다")
    void registeringProtectorHasNoPatientCode() {
        when(userRepository.existsByPhone("01011110018")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = userService.register(signupRequest("01011110018", "abcd1234", "PROTECTOR"));

        assertNull(saved.getPatientCode());
        verify(recallQuestionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("규칙에 맞지 않는 비밀번호로는 가입할 수 없다 (400)")
    void registerRejectsWeakPassword() {
        ResponseStatusException e = assertStatus(HttpStatus.BAD_REQUEST,
                () -> userService.register(signupRequest("01011110019", "12345678", "PATIENT")));

        assertEquals(PasswordPolicy.MESSAGE, e.getReason());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("이미 가입된 전화번호로는 가입할 수 없다")
    void registerRejectsDuplicatePhone() {
        when(userRepository.existsByPhone("01011110020")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> userService.register(signupRequest("01011110020", "abcd1234", "PATIENT")));
        verify(userRepository, never()).save(any(User.class));
    }

    // ───────────── 로그인 / 비밀번호 변경 ─────────────

    @Test
    @DisplayName("비밀번호가 맞으면 로그인되고, 틀리면 null 을 돌려준다")
    void loginChecksPasswordAgainstStoredHash() {
        User stored = user(1, "01011110021", "PATIENT");
        stored.setPassword(passwordEncoder.encode("abcd1234"));
        when(userRepository.findByPhone(stored.getPhone())).thenReturn(Optional.of(stored));

        assertSame(stored, userService.login(stored.getPhone(), "abcd1234"));
        assertNull(userService.login(stored.getPhone(), "wrong9999"));
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 새 비밀번호의 해시로 교체된다")
    void resetPasswordReplacesHash() {
        User stored = user(1, "01011110022", "PATIENT");
        stored.setPassword(passwordEncoder.encode("abcd1234"));
        when(userRepository.findByPhone(stored.getPhone())).thenReturn(Optional.of(stored));

        userService.resetPassword(stored.getPhone(), "newpass2026");

        assertTrue(passwordEncoder.matches("newpass2026", stored.getPassword()));
        assertFalse(passwordEncoder.matches("abcd1234", stored.getPassword()));
    }

    @Test
    @DisplayName("규칙에 맞지 않는 비밀번호로는 바꿀 수 없다 (400)")
    void resetPasswordRejectsWeakPassword() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> userService.resetPassword("01011110023", "short1"));

        verify(userRepository, never()).findByPhone(any());
    }

    @Test
    @DisplayName("저장 직전의 사용자 정보에 평문 비밀번호가 남지 않는다")
    void plainPasswordNeverReachesRepository() {
        when(userRepository.existsByPhone("01011110024")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.register(signupRequest("01011110024", "abcd1234", "PROTECTOR"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNotEquals("abcd1234", captor.getValue().getPassword());
    }
}
