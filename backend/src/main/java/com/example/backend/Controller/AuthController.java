package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.LoginDTO;
import com.example.backend.Model.DTO.SignupDTO;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<User>> signup(@RequestBody SignupDTO signupDTO) {
        try {
            User user = new User();
            user.setEmail(signupDTO.getEmail());
            user.setPassword(signupDTO.getPassword());
            user.setName(signupDTO.getName());
            user.setRole(signupDTO.getRole());
            user.setAgeGroup(signupDTO.getAgeGroup());
            user.setGender(signupDTO.getGender());

            User savedUser = userService.register(user);
            return ResponseEntity.ok(ApiResponse.success(savedUser));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "회원가입 실패"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginDTO loginDTO) {
        User user = userService.login(loginDTO.getEmail(), loginDTO.getPassword());

        if (user != null) {
            return ResponseEntity.ok(ApiResponse.success(user));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(401, "아이디 또는 비밀번호가 틀렸습니다."));
        }
    }
}