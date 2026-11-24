package com.example.backend.Service;

import com.example.backend.Model.Entity.chat.ChatSession;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.AudioRecordRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.ChatSessionRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import com.example.backend.Model.Repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 음성 업로드 시 사용자·세션을 먼저 확인하고, 잘못된 요청이면 파일을 올리지 않는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class VoiceServiceUploadTest {

    @Mock private S3Service s3Service;
    @Mock private AudioRecordRepository audioRecordRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private RecallQuestionRepository recallQuestionRepository;
    @Mock private STTService sttService;
    @Mock private AiProcessTriggerService aiProcessTriggerService;

    @InjectMocks private VoiceService voiceService;

    private final MockMultipartFile audio =
            new MockMultipartFile("file", "answer.wav", "audio/wav", new byte[]{1, 2, 3});

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private ChatSession sessionOf(User owner) {
        ChatSession session = new ChatSession();
        session.setId(10L);
        session.setUser(owner);
        return session;
    }

    @Test
    @DisplayName("없는 사용자면 파일을 올리지 않는다")
    void unknownUserDoesNotUploadFile() throws Exception {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> voiceService.uploadAndSave(1, 10L, null, null, audio));

        verify(s3Service, never()).uploadFile(any());
    }

    @Test
    @DisplayName("없는 세션이면 파일을 올리지 않는다")
    void unknownSessionDoesNotUploadFile() throws Exception {
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1)));
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> voiceService.uploadAndSave(1, 10L, null, null, audio));

        verify(s3Service, never()).uploadFile(any());
    }

    @Test
    @DisplayName("다른 사용자의 세션에는 음성을 올릴 수 없고 파일도 올라가지 않는다")
    void sessionOfAnotherUserIsRejected() throws Exception {
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1)));
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(sessionOf(user(2))));

        assertThrows(IllegalArgumentException.class,
                () -> voiceService.uploadAndSave(1, 10L, null, null, audio));

        verify(s3Service, never()).uploadFile(any());
        verify(audioRecordRepository, never()).save(any());
    }
}
