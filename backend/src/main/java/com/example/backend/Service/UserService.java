package com.example.backend.Service;

import com.example.backend.Model.Entity.recall.RecallQuestion;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import com.example.backend.Model.Repository.UserRepository;
import com.example.backend.Util.DefaultRecallQuestions;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RecallQuestionRepository recallQuestionRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public User register(User dto) {

        // 중복 체크
        if (userRepository.existsByPhone(dto.getPhone())) {
            throw new IllegalArgumentException("이미 등록된 전화번호입니다. 비밀번호 찾기를 이용해주세요.");
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        user.setName(dto.getName());
        user.setRole(dto.getRole());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        if ("PATIENT".equals(dto.getRole())) {
            // 환자 -> 보호자가 앱에서 환자와 연동할 수 있도록 고유한 8자리 초대 코드를 발급
            String randomCode;
            do {
                randomCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            } while (userRepository.existsByPatientCode(randomCode));
            user.setPatientCode(randomCode);
        } else {
            // 보호자 -> 코드가 필요 없으므로 null로 처리
            user.setPatientCode(null);
        }
        User savedUser = userRepository.save(user);

        // 연관 데이터(질문 리스트) 생성
        if ("PATIENT".equals(savedUser.getRole())) {
            setupDefaultRecallQuestions(savedUser);
        }

        return savedUser;
    }

    private void setupDefaultRecallQuestions(User user) {
        // 상수 클래스에서 질문 목록을 가져와 RecallQuestion 엔티티로 변환
        List<RecallQuestion> initialQuestions = DefaultRecallQuestions.DEFAULT_QUESTIONS.stream()
                .map(questionText -> {
                    RecallQuestion recallQuestion = new RecallQuestion();
                    recallQuestion.setUser(user);
                    recallQuestion.setQuestionText(questionText);
                    recallQuestion.setQuestionType("DEFAULT");
                    recallQuestion.setCategory("기본정보");
                    return recallQuestion;
                })
                .collect(Collectors.toList());

        // 3. DB에 일괄 저장 (saveAll을 사용해 성능 최적화)
        recallQuestionRepository.saveAll(initialQuestions);
    }
    // 보호자-환자 연동
    @Transactional
    public void linkProtector(Integer protectorId, String patientCode) {
        // 입력된 코드로 환자 찾기
        User patient = userRepository.findByPatientCode(patientCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자 코드입니다."));

        // 연동을 시도하는 보호자 정보 가져오기
        User protector = userRepository.findById(protectorId)
                .orElseThrow(() -> new IllegalArgumentException("보호자 정보를 찾을 수 없습니다."));

        // 역할 검증
        if (!"PROTECTOR".equals(protector.getRole())) {
            throw new IllegalStateException("보호자 계정만 환자를 등록할 수 있습니다.");
        }

        protector.setTargetPatient(patient);
    }

    public boolean existsByPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

    @Transactional
    public void resetPassword(String phone, String newPassword) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setPassword(passwordEncoder.encode(newPassword));
    }

    // 회원 탈퇴
    @Transactional
    public void deleteUser(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        userRepository.delete(user);
    }

    // 로그인
    public User login(String phone, String password) {
        return userRepository.findByPhone(phone)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .orElse(null);
    }
}