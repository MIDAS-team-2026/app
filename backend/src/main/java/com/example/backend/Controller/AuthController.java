package com.example.backend.Controller;

import com.example.backend.Model.DTO.LoginDTO;
import com.example.backend.Model.DTO.SignupDTO;
import com.example.backend.Model.Entity.User;
import com.example.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupDTO signupDTO) {
        User user = new User();
        user.setEmail(signupDTO.getEmail());
        user.setPassword(signupDTO.getPassword());
        user.setName(signupDTO.getName());
        user.setPatientCode(signupDTO.getPatientCode());

        User savedUser = userService.register(user);
        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO) {
        // 이제 credentials.get("email") 대신 getter를 사용합니다.
        String email = loginDTO.getEmail();
        String password = loginDTO.getPassword();

        User user = userService.login(email, password);

        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("아이디 또는 비밀번호가 틀렸습니다.");
        }
    }
}