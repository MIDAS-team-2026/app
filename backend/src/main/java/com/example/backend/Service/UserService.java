package com.example.backend.Service;

import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // 회원가입
    @Transactional
    public User register(User dto) {

        // 중복 체크
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setName(dto.getName());
        user.setRole(dto.getRole());
        user.setPassword(dto.getPassword());

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

        return userRepository.save(user);
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

    // 로그인
    public User login(String email, String password) {
        return userRepository.findByEmail(email)
                .filter(u -> u.getPassword().equals(password))
                .orElse(null);
    }
}