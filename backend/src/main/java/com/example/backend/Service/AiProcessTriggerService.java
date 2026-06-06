package com.example.backend.Service;

import com.example.backend.Model.Entity.chat.AudioRecord;
import com.example.backend.Model.Repository.AiAnalysisRepository.AudioRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiProcessTriggerService {

    @Value("${ai.python.url:http://localhost:8000}")
    private String pythonBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final AudioRecordRepository audioRecordRepository;

    public void triggerProcess(Long recordId, Long sessionId, Integer userId) {

        try {

            AudioRecord record = audioRecordRepository.findById(recordId)
                    .orElseThrow(() ->
                            new RuntimeException("AudioRecord 없음: " + recordId));

            Map<String, Object> body = new HashMap<>();

            body.put("recordId", recordId);
            body.put("sessionId", sessionId);
            body.put("userId", userId);

            body.put("audioPath", record.getAudioFilePath());
            body.put("transcriptText", record.getTranscriptText());
            body.put("durationSec",
                    record.getAudioDuration() == null
                            ? 0.0
                            : record.getAudioDuration().doubleValue());

            log.info(
                    "Python 분석 요청 recordId={} audioPath={} transcriptLen={}",
                    recordId,
                    record.getAudioFilePath(),
                    record.getTranscriptText() == null
                            ? 0
                            : record.getTranscriptText().length()
            );

            restTemplate.postForEntity(
                    pythonBaseUrl + "/process",
                    body,
                    String.class
            );

            log.info("Python /process 요청 완료 recordId={}", recordId);

        } catch (Exception e) {

            log.error(
                    "Python /process 요청 실패 recordId={} : {}",
                    recordId,
                    e.getMessage(),
                    e
            );
        }
    }
}
