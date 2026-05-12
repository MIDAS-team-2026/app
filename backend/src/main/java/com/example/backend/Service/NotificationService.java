package com.example.backend.Service;

import com.example.backend.Model.Entity.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final FcmService fcmService;

    public void notifyAllProtectors(User patient, String title, String content) {
        List<User> protectors = patient.getProtectors();

        if (protectors != null && !protectors.isEmpty()) {
            for (User protector : protectors) {
                String token = protector.getFcmToken();
                if (token != null && !token.isEmpty()) {
                    fcmService.sendPushNotification(token, title, content);
                    log.info("알림 발송 완료: 대상={}, 제목={}", protector.getEmail(), title);
                }
            }
        } else {
            log.warn("알림을 보낼 보호자가 없습니다. 환자={}", patient.getName());
        }
    }
}