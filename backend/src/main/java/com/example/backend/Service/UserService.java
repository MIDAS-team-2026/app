package com.example.backend.Service;

import com.example.backend.Model.Entity.recall.RecallQuestion;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import com.example.backend.Model.Repository.UserRepository;
import com.example.backend.Util.DefaultRecallQuestions;
import com.example.backend.Util.JwtTokenProvider;
import com.example.backend.Util.PasswordPolicy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RecallQuestionRepository recallQuestionRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // 회원가입
    @Transactional
    public User register(User dto) {

        // 비밀번호 규칙 검사 (영문 + 숫자 포함 8자 이상)
        PasswordPolicy.validate(dto.getPassword());

        // 중복 체크
        if (userRepository.existsByPhone(dto.getPhone())) {
            throw new IllegalArgumentException("이미 등록된 전화번호입니다. 비밀번호 찾기를 이용해주세요.");
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        user.setName(dto.getName());
        user.setRole(dto.getRole());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setAgeGroup(dto.getAgeGroup());
        user.setGender(dto.getGender());

        if ("PATIENT".equalsIgnoreCase(dto.getRole())) {
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

        // 환자 회원가입 시 기본 회상 질문 생성
        if ("PATIENT".equalsIgnoreCase(savedUser.getRole())) {
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

        // DB에 일괄 저장
        recallQuestionRepository.saveAll(initialQuestions);
    }

    // 보호자-환자 연동 해제
    @Transactional
    public void unlinkPatient(Integer protectorId, Integer patientId) {
        User protector = userRepository.findById(protectorId)
                .orElseThrow(() -> new IllegalArgumentException("보호자 정보를 찾을 수 없습니다."));

        boolean removed = protector.getPatients().removeIf(p -> p.getId().equals(patientId));
        if (!removed) {
            throw new IllegalArgumentException("연동된 환자가 아닙니다.");
        }
    }

    // 보호자-환자 연동
    @Transactional
    public void linkProtector(Integer protectorId, String patientCode) {
        User patient = userRepository.findByPatientCode(patientCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자 코드입니다."));

        User protector = userRepository.findById(protectorId)
                .orElseThrow(() -> new IllegalArgumentException("보호자 정보를 찾을 수 없습니다."));

        if (!"PROTECTOR".equalsIgnoreCase(protector.getRole())) {
            throw new IllegalStateException("보호자 계정만 환자를 등록할 수 있습니다.");
        }

        // 이미 연동된 경우 중복 추가 방지
        boolean alreadyLinked = protector.getPatients().stream()
                .anyMatch(p -> p.getId().equals(patient.getId()));
        if (alreadyLinked) {
            throw new IllegalStateException("이미 연동된 환자입니다.");
        }

        protector.getPatients().add(patient);
    }

    // 환자 코드로 환자 조회
    public User findByPatientCode(String patientCode) {
        return userRepository.findByPatientCode(patientCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자 코드입니다."));
    }

    public boolean existsByPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

    @Transactional
    public void resetPassword(String phone, String newPassword) {
        // 비밀번호 규칙 검사 (영문 + 숫자 포함 8자 이상)
        PasswordPolicy.validate(newPassword);

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

    // 환자에 연결된 보호자 목록
    public List<User> getGuardiansByPatient(Integer patientId) {
        return userRepository.findByPatients_Id(patientId);
    }

    // 보호자가 연결한 환자 목록
    public List<User> getPatientsByProtector(Integer protectorId) {
        User protector = userRepository.findById(protectorId)
                .orElseThrow(() -> new IllegalArgumentException("보호자 정보를 찾을 수 없습니다."));

        if (!"PROTECTOR".equalsIgnoreCase(protector.getRole())) {
            throw new IllegalStateException("보호자 계정만 연결된 환자 목록을 조회할 수 있습니다.");
        }

        return protector.getPatients();
    }

    // Authorization 헤더의 JWT로 요청자를 확인한다.
    // 토큰이 없거나, 유효하지 않거나, 토큰의 사용자가 존재하지 않으면 401
    public User getAuthenticatedUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        String token = authorizationHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "유효하지 않은 토큰입니다."
            );
        }

        String requesterPhone = jwtTokenProvider.getPhoneFromToken(token);

        return userRepository.findByPhone(requesterPhone)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "토큰 사용자 정보를 찾을 수 없습니다."
                ));
    }

    // 토큰의 주인이 해당 전화번호의 사용자인지 확인한다.
    // 예외를 던지지 않고 true/false 로만 알려준다. (비밀번호 변경 시 본인 확인용)
    public boolean isTokenOwner(String authorizationHeader, String phone) {
        if (phone == null || authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            return false;
        }

        return phone.equals(jwtTokenProvider.getPhoneFromToken(token));
    }

    // 본인 확인: 요청자가 userId 본인일 때만 허용 (아니면 403)
    public User validateSelfAccess(String authorizationHeader, Integer userId) {
        User requester = getAuthenticatedUser(authorizationHeader);

        if (userId == null || !requester.getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인 계정으로만 요청할 수 있습니다."
            );
        }

        return requester;
    }

    // 연동 해제 권한 검사: 해당 보호자 본인 또는 해당 환자 본인일 때만 허용 (아니면 403)
    public User validateLinkAccess(String authorizationHeader, Integer protectorId, Integer patientId) {
        User requester = getAuthenticatedUser(authorizationHeader);

        boolean isProtector = requester.getId().equals(protectorId);
        boolean isPatient = requester.getId().equals(patientId);

        if (!isProtector && !isPatient) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 연동 정보를 변경할 권한이 없습니다."
            );
        }

        return requester;
    }

    // 민감정보 접근 권한 검사
    // 허용 조건:
    // 1. 요청자가 targetUserId 본인인 경우
    // 2. 요청자가 보호자이고, targetUserId 환자와 연결되어 있는 경우
    @Transactional
    public User validatePatientAccess(String authorizationHeader, Integer targetUserId) {
        User requester = getAuthenticatedUser(authorizationHeader);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "대상 사용자를 찾을 수 없습니다."
                ));

        // 본인 정보 접근 허용
        if (requester.getId().equals(targetUser.getId())) {
            return requester;
        }

        // 연결된 보호자 접근 허용
        if ("PROTECTOR".equalsIgnoreCase(requester.getRole())) {
            boolean linkedPatient = requester.getPatients().stream()
                    .anyMatch(patient -> patient.getId().equals(targetUserId));

            if (linkedPatient) {
                return requester;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "해당 사용자 정보에 접근할 권한이 없습니다."
        );
    }
}