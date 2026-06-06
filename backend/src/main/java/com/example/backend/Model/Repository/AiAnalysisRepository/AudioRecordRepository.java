package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.chat.AudioRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AudioRecordRepository extends JpaRepository<AudioRecord, Long> {
    @Query("SELECT COALESCE(MAX(a.turnOrder), 0) FROM AudioRecord a WHERE a.chatSession.id = :sessionId")
    Integer findMaxTurnOrderByChatSessionId(@Param("sessionId") Long sessionId);

    List<AudioRecord> findByChatSession_Id(Long sessionId);

    Optional<AudioRecord> findFirstByUser_IdAndRecallQuestionIdAndAnswerRoleOrderByRecordedAtDesc(
            Integer userId,
            Long recallQuestionId,
            String answerRole);

    // 사용자의 세션에서 아직 답변되지 않은 회상 질문(INITIAL)이 있는지 확인
    @Query("""
        SELECT a FROM AudioRecord a
        WHERE a.chatSession.id = :sessionId
          AND a.user.id = :userId
          AND a.recallQuestionId IS NOT NULL
          AND a.answerRole = 'INITIAL'
          AND NOT EXISTS (
              SELECT 1 FROM AudioRecord r
              WHERE r.recallQuestionId = a.recallQuestionId
                AND r.answerRole = 'RECALL'
          )
        ORDER BY a.recordedAt DESC
        """)
    Optional<AudioRecord> findPendingRecallInitialRecord(
            @Param("sessionId") Long sessionId,
            @Param("userId") Integer userId);
}