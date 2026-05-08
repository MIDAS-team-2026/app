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
            String randomCode = UUID.randomUUID().toString().substring(0, 8);
            user.setPatientCode(randomCode);
        } else {
            // 보호자 -> 코드가 필요 없으므로 null로 처리
            user.setPatientCode(null);
        }

        return userRepository.save(user);
    }

    // 로그인
    public User login(String email, String password) {
        return userRepository.findByEmail(email)
                .filter(u -> u.getPassword().equals(password))
                .orElse(null);
    }
}