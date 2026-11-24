package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.RecordAnalysisDTO;
import com.example.backend.Model.Entity.analysis.SpeechAnalysisResult;
import com.example.backend.Model.Entity.analysis.TextAnalysisResult;
import com.example.backend.Model.Entity.chat.AudioRecord;
import com.example.backend.Model.Repository.AiAnalysisRepository.AudioRecordRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.ChatSessionRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.LinguisticMarkerRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallAnalysisRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.RiskAnalysisRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.SpeechAnalysisResultRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.TextAnalysisResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock private AudioRecordRepository audioRecordRepository;
    @Mock private RecallQuestionRepository recallQuestionRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private RecallAnalysisRepository recallAnalysisRepository;
    @Mock private RiskAnalysisRepository riskAnalysisRepository;
    @Mock private SpeechAnalysisResultRepository speechAnalysisResultRepository;
    @Mock private TextAnalysisResultRepository textAnalysisResultRepository;
    @Mock private LinguisticMarkerRepository linguisticMarkerRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private AiAnalysisService aiAnalysisService;

    @Test
    void saveRecordAnalysisUpdatesExistingResultsForTheSameRecord() {
        long recordId = 42L;
        AudioRecord record = new AudioRecord();
        record.setId(recordId);

        SpeechAnalysisResult existingSpeech = new SpeechAnalysisResult();
        existingSpeech.setId(100L);
        existingSpeech.setAudioRecord(record);

        TextAnalysisResult existingText = new TextAnalysisResult();
        existingText.setId(200L);
        existingText.setAudioRecord(record);

        RecordAnalysisDTO.SpeechAnalysisData speech = new RecordAnalysisDTO.SpeechAnalysisData();
        speech.setPauseRatio(0.25F);
        speech.setVoiceActivityRatio(0.75F);
        speech.setSpeechAbnormalityScore(4.35F);

        RecordAnalysisDTO.TextAnalysisData text = new RecordAnalysisDTO.TextAnalysisData();
        text.setWordCount(7);

        RecordAnalysisDTO dto = new RecordAnalysisDTO();
        dto.setRecordId(recordId);
        dto.setTranscriptText("재분석 결과");
        dto.setSpeechAnalysis(speech);
        dto.setTextAnalysis(text);

        when(audioRecordRepository.findByIdForUpdate(recordId)).thenReturn(Optional.of(record));
        when(speechAnalysisResultRepository.findFirstByAudioRecord_IdOrderByIdDesc(recordId))
                .thenReturn(Optional.of(existingSpeech));
        when(textAnalysisResultRepository.findFirstByAudioRecord_IdOrderByIdDesc(recordId))
                .thenReturn(Optional.of(existingText));

        aiAnalysisService.saveRecordAnalysis(dto);

        ArgumentCaptor<SpeechAnalysisResult> speechCaptor =
                ArgumentCaptor.forClass(SpeechAnalysisResult.class);
        ArgumentCaptor<TextAnalysisResult> textCaptor =
                ArgumentCaptor.forClass(TextAnalysisResult.class);

        verify(speechAnalysisResultRepository).save(speechCaptor.capture());
        verify(textAnalysisResultRepository).save(textCaptor.capture());

        assertSame(existingSpeech, speechCaptor.getValue());
        assertSame(existingText, textCaptor.getValue());
        assertEquals(100L, speechCaptor.getValue().getId());
        assertEquals(200L, textCaptor.getValue().getId());
        assertEquals(0.25F, speechCaptor.getValue().getPauseRatio());
        assertEquals(0.75F, speechCaptor.getValue().getVoiceActivityRatio());
        assertEquals(4.35F, speechCaptor.getValue().getSpeechAbnormalityScore());
        assertEquals(7, textCaptor.getValue().getWordCount());
        assertEquals("재분석 결과", record.getTranscriptText());
    }

}
