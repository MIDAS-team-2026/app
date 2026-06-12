package com.example.backend.Service;

import com.example.backend.Model.Entity.chat.AudioRecord;
import com.example.backend.Model.Repository.AiAnalysisRepository.AudioRecordRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class STTService {

    private final AudioRecordRepository audioRecordRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.python.url:http://localhost:8000}")
    private String pythonBaseUrl;

    @Transactional
    public SttResult transcribeAndSave(Long recordId) {
        AudioRecord record = audioRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("AudioRecord not found. recordId=" + recordId));

        SttResult result = transcribe(record.getAudioFilePath());
        record.setTranscriptText(result.getTranscriptText());
        record.setSttConfidence(result.getConfidence());
        audioRecordRepository.save(record);
        return result;
    }

    public SttResult transcribe(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            throw new IllegalArgumentException("audioUrl is required.");
        }

        String endpoint = pythonBaseUrl + "/api/stt";
        SttRequest request = new SttRequest(audioUrl, "ko");

        try {
            SttResponse response = restTemplate.postForObject(endpoint, request, SttResponse.class);
            if (response == null || response.getTranscriptText() == null) {
                throw new IllegalStateException("Empty STT response from FastAPI server.");
            }
            return new SttResult(response.getTranscriptText(), response.getConfidence(), response.getModelName());
        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "Failed to call FastAPI STT server (" + pythonBaseUrl + "/api/stt): " + e.getMessage(), e);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class SttRequest {
        private String audioUrl;
        private String language;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class SttResponse {
        private String transcriptText;
        private Float confidence;
        private String modelName;
    }

    @Getter
    @AllArgsConstructor
    public static class SttResult {
        private String transcriptText;
        private Float confidence;
        private String modelName;
    }
}
