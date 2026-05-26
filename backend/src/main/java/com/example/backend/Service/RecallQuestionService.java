package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.RecallQuestionResponseDTO;
import com.example.backend.Model.DTO.analysis.RecallQuestionUpdateDTO;
import com.example.backend.Model.Entity.recall.RecallKeyword;
import com.example.backend.Model.Entity.recall.RecallQuestion;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecallQuestionService {

    private final RecallQuestionRepository recallQuestionRepository;

    @Transactional(readOnly = true)
    public List<RecallQuestionResponseDTO> getQuestionsByUserId(Integer userId) {
        List<RecallQuestion> questions = recallQuestionRepository.findByUser_Id(userId);

        return questions.stream().map(question -> {
            RecallQuestionResponseDTO dto = new RecallQuestionResponseDTO();
            dto.setQuestionId(question.getId());
            dto.setQuestionText(question.getQuestionText());
            dto.setQuestionType(question.getQuestionType());
            dto.setCategory(question.getCategory());

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
}