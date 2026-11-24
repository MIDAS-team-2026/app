package com.example.backend.Controller;

import com.example.backend.Exception.GlobalExceptionHandler;
import com.example.backend.Model.DTO.analysis.AiReplyResponseDTO;
import com.example.backend.Model.DTO.analysis.VoiceResponseDTO;
import com.example.backend.Model.DTO.chat.SessionStartResponseDTO;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import com.example.backend.Model.Repository.UserRepository;
import com.example.backend.Service.AiAnalysisService;
import com.example.backend.Service.ChatSessionService;
import com.example.backend.Service.UserService;
import com.example.backend.Service.VoiceService;
import com.example.backend.Util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱이 호출하는 대화 API(세션 시작, 음성 업로드, 답변 조회, 세션 종료)의 접근 권한 검사를 검증한다.
 * 권한 검사(UserService, JWT)는 실제 구현을 쓰고, DB와 나머지 서비스는 Mockito 로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class VoiceAccessControlTest {

    @Mock private UserRepository userRepository;
    @Mock private RecallQuestionRepository recallQuestionRepository;
    @Mock private VoiceService voiceService;
    @Mock private ChatSessionService chatSessionService;
    @Mock private AiAnalysisService aiAnalysisService;

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(
                userRepository, recallQuestionRepository, new BCryptPasswordEncoder(), jwtTokenProvider);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new VoiceController(voiceService, userService),
                        new ChatSessionController(chatSessionService, userService),
                        new AiAnalysisController(aiAnalysisService, chatSessionService, userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private User user(int id, String phone, String role) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setRole(role);
        return user;
    }

    // DB 에 이 사용자가 있는 것처럼 만들어 두고, 그 사용자의 로그인 토큰 헤더를 돌려준다.
    private String loginAs(User user) {
        when(userRepository.findByPhone(user.getPhone())).thenReturn(Optional.of(user));
        return "Bearer " + jwtTokenProvider.createToken(user.getPhone(), user.getRole());
    }

    private MockMultipartFile audio() {
        return new MockMultipartFile("file", "answer.wav", "audio/wav", new byte[]{1, 2, 3});
    }

    // ───────────── 세션 시작 ─────────────

    @Test
    @DisplayName("토큰 없이 세션을 시작하면 401")
    void startSessionWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/chat/session/start").param("userId", "1"))
                .andExpect(status().isUnauthorized());

        verify(chatSessionService, never()).startSession(anyInt());
    }

    @Test
    @DisplayName("다른 사용자의 이름으로 세션을 시작할 수 없다 (403)")
    void startSessionForAnotherUserIsForbidden() throws Exception {
        User attacker = user(2, "01033330001", "PATIENT");
        String token = loginAs(attacker);
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "01033330002", "PATIENT")));

        mockMvc.perform(post("/api/chat/session/start").param("userId", "1").header("Authorization", token))
                .andExpect(status().isForbidden());

        verify(chatSessionService, never()).startSession(anyInt());
    }

    @Test
    @DisplayName("본인은 자신의 세션을 시작할 수 있다")
    void userCanStartOwnSession() throws Exception {
        User patient = user(1, "01033330003", "PATIENT");
        String token = loginAs(patient);
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));
        when(chatSessionService.startSession(1))
                .thenReturn(SessionStartResponseDTO.builder().sessionId(10L).build());

        mockMvc.perform(post("/api/chat/session/start").param("userId", "1").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(10));
    }

    // ───────────── 음성 업로드 ─────────────

    @Test
    @DisplayName("토큰 없이 음성을 올리면 401 이고 저장되지 않는다")
    void uploadWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/voice/upload").file(audio())
                        .param("userId", "1").param("sessionId", "10"))
                .andExpect(status().isUnauthorized());

        verify(voiceService, never()).uploadAndSave(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("다른 사용자의 음성으로 올릴 수 없다 (403)")
    void uploadAsAnotherUserIsForbidden() throws Exception {
        User attacker = user(2, "01033330004", "PATIENT");
        String token = loginAs(attacker);
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "01033330005", "PATIENT")));

        mockMvc.perform(multipart("/api/voice/upload").file(audio())
                        .param("userId", "1").param("sessionId", "10").header("Authorization", token))
                .andExpect(status().isForbidden());

        verify(voiceService, never()).uploadAndSave(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("본인은 자신의 음성을 올릴 수 있다")
    void userCanUploadOwnVoice() throws Exception {
        User patient = user(1, "01033330006", "PATIENT");
        String token = loginAs(patient);
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));
        when(voiceService.uploadAndSave(any(), any(), any(), any(), any()))
                .thenReturn(VoiceResponseDTO.builder().audioRecordId(77L).build());

        mockMvc.perform(multipart("/api/voice/upload").file(audio())
                        .param("userId", "1").param("sessionId", "10").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioRecordId").value(77));
    }

    // ───────────── AI 답변 조회 ─────────────

    @Test
    @DisplayName("다른 사용자의 녹음에 대한 AI 답변은 조회할 수 없다 (403)")
    void replyOfAnotherUsersRecordIsForbidden() throws Exception {
        User attacker = user(2, "01033330007", "PATIENT");
        String token = loginAs(attacker);
        when(voiceService.getRecordOwnerId(77L)).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "01033330008", "PATIENT")));

        mockMvc.perform(get("/api/voice/reply/77").header("Authorization", token))
                .andExpect(status().isForbidden());

        verify(voiceService, never()).getAiReply(anyLong());
    }

    @Test
    @DisplayName("토큰 없이 AI 답변을 조회하면 401")
    void replyWithoutTokenIsUnauthorized() throws Exception {
        when(voiceService.getRecordOwnerId(77L)).thenReturn(1);

        mockMvc.perform(get("/api/voice/reply/77"))
                .andExpect(status().isUnauthorized());

        verify(voiceService, never()).getAiReply(anyLong());
    }

    @Test
    @DisplayName("본인은 자신의 녹음에 대한 AI 답변을 조회할 수 있다")
    void userCanPollOwnReply() throws Exception {
        User patient = user(1, "01033330009", "PATIENT");
        String token = loginAs(patient);
        when(voiceService.getRecordOwnerId(77L)).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));
        when(voiceService.getAiReply(77L)).thenReturn(new AiReplyResponseDTO(77L, "네, 그러셨군요."));

        mockMvc.perform(get("/api/voice/reply/77").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyText").value("네, 그러셨군요."));
    }

    @Test
    @DisplayName("없는 녹음의 AI 답변을 조회하면 404")
    void replyOfMissingRecordIsNotFound() throws Exception {
        when(voiceService.getRecordOwnerId(999L))
                .thenThrow(new IllegalArgumentException("존재하지 않는 녹음 기록입니다. recordId=999"));

        mockMvc.perform(get("/api/voice/reply/999"))
                .andExpect(status().isNotFound());
    }

    // ───────────── 세션 종료 ─────────────

    @Test
    @DisplayName("다른 사용자의 세션은 종료시킬 수 없다 (403)")
    void completeAnotherUsersSessionIsForbidden() throws Exception {
        User attacker = user(2, "01033330010", "PATIENT");
        String token = loginAs(attacker);
        when(chatSessionService.getSessionOwnerId(10L)).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "01033330011", "PATIENT")));

        mockMvc.perform(post("/api/ai/analysis/chat/session/10/complete").header("Authorization", token))
                .andExpect(status().isForbidden());

        verify(chatSessionService, never()).endSession(anyLong());
        verify(aiAnalysisService, never()).completeSessionAndTriggerBatch(anyLong());
    }

    @Test
    @DisplayName("세션의 주인 본인과 연동된 보호자는 세션을 종료할 수 있다")
    void ownerOrLinkedProtectorCanCompleteSession() throws Exception {
        User patient = user(1, "01033330012", "PATIENT");
        User protector = user(3, "01033330013", "PROTECTOR");
        protector.getPatients().add(patient);
        when(chatSessionService.getSessionOwnerId(10L)).thenReturn(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(patient));

        mockMvc.perform(post("/api/ai/analysis/chat/session/10/complete")
                        .header("Authorization", loginAs(patient)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/analysis/chat/session/10/complete")
                        .header("Authorization", loginAs(protector)))
                .andExpect(status().isOk());
    }
}
