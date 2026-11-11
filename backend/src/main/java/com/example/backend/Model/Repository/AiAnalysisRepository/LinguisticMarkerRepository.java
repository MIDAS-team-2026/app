package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.analysis.LinguisticMarkerResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinguisticMarkerRepository extends JpaRepository<LinguisticMarkerResult, Long> {

    Optional<LinguisticMarkerResult> findByChatSession_Id(Long sessionId);

    // 개인 기준선 계산용: 이 사용자의 과거 세션 지표들을 최신순으로 가져온다.
    // (현재 세션은 아직 저장 전이라 자연히 제외된다.)
    List<LinguisticMarkerResult> findByChatSession_User_IdOrderByAnalyzedAtDesc(Integer userId);
}
