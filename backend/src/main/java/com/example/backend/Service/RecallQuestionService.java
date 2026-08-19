package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.RecallQuestionResponseDTO;
import com.example.backend.Model.DTO.analysis.RecallQuestionUpdateDTO;
import com.example.backend.Model.Entity.recall.RecallKeyword;
import com.example.backend.Model.Entity.recall.RecallQuestion;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.backend.Model.DTO.analysis.RecallQuestionCreateDTO;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RecallQuestionService {

    private final RecallQuestionRepository recallQuestionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RecallQuestionResponseDTO> getQuestionsByUserId(Integer userId) {
        List<RecallQuestion> questions = recallQuestionRepository.findByUser_Id(userId);

        return questions.stream().map(question -> {
            RecallQuestionResponseDTO dto = new RecallQuestionResponseDTO();
            dto.setQuestionId(question.getId());
            dto.setQuestionText(question.getQuestionText());
            dto.setQuestionType(question.getQuestionType());
            dto.setCategory(question.getCategory());
            dto.setExpectedAnswer(question.getExpectedAnswer());
            dto.setEventId(question.getEventId());
            dto.setClueId(question.getClueId());
            dto.setSourceRecordId(question.getSourceRecordId());
            dto.setAnswerType(question.getAnswerType());

            // Keyword 엔티티 리스트에서 'keywordText'만 추출하여 순수 문자열 리스트로 변환
            List<String> keywordList = question.getKeywords().stream()
                    .map(RecallKeyword::getKeywordText)
                    .collect(Collectors.toList());

            dto.setKeywords(keywordList);
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void updateQuestionInfo(Long id, RecallQuestionUpdateDTO dto) {
        RecallQuestion question = recallQuestionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회상 질문을 찾을 수 없습니다."));

        if (dto.getQuestionType() != null) question.setQuestionType(dto.getQuestionType());
        if (dto.getExpectedAnswer() != null) question.setExpectedAnswer(dto.getExpectedAnswer());
        if (dto.getKeywords() != null) {
            List<RecallKeyword> keywordEntities = dto.getKeywords().stream()
                    .map(keywordText -> {
                        RecallKeyword rk = new RecallKeyword();
                        rk.setKeywordText(keywordText);
                        rk.setRecallQuestion(question); // 연관관계 편의 메서드나 필드가 있다면 세팅
                        return rk;
                    })
                    .collect(Collectors.toList());
            question.setKeywords(keywordEntities);
        }

        recallQuestionRepository.save(question);
    }

    @Transactional
    public RecallQuestionResponseDTO createQuestion(RecallQuestionCreateDTO dto) {

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (dto.getClueId() != null && !dto.getClueId().isBlank()) {
            RecallQuestion existing = recallQuestionRepository
                    .findByUser_IdAndClueId(dto.getUserId(), dto.getClueId())
                    .orElse(null);

            if (existing != null) {
                return toResponse(existing);
            }
        }

        RecallQuestion question = new RecallQuestion();
        question.setUser(user);
        question.setQuestionText(dto.getQuestionText());
        question.setQuestionType(
                dto.getQuestionType() != null ?
                        dto.getQuestionType() : "RECALL"
        );
        question.setCategory(
                dto.getCategory() != null ?
                        dto.getCategory() : "CONVERSATION"
        );
        question.setExpectedAnswer(dto.getExpectedAnswer());
        question.setEventId(dto.getEventId());
        question.setClueId(dto.getClueId());
        question.setSourceRecordId(dto.getSourceRecordId());
        question.setAnswerType(dto.getAnswerType());

        RecallQuestion saved = recallQuestionRepository.save(question);

        return toResponse(saved);
    }

    private RecallQuestionResponseDTO toResponse(RecallQuestion saved) {
        RecallQuestionResponseDTO response = new RecallQuestionResponseDTO();
        response.setQuestionId(saved.getId());
        response.setQuestionText(saved.getQuestionText());
        response.setQuestionType(saved.getQuestionType());
        response.setCategory(saved.getCategory());
        response.setExpectedAnswer(saved.getExpectedAnswer());
        response.setEventId(saved.getEventId());
        response.setClueId(saved.getClueId());
        response.setSourceRecordId(saved.getSourceRecordId());
        response.setAnswerType(saved.getAnswerType());

        return response;
    }
}
