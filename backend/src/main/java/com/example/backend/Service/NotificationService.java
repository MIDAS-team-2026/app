package com.example.backend.Service;

import com.example.backend.Model.Entity.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {
    // TODO: FCM(Firebase) 연결 로직 추가
    public void sendToProtector(User protector, String title, String content) {
        log.info("알림 발송 대상: [{}], 제목: {}, 내용: {}", protector.getEmail(), title, content);
    }

    // 환자와 연결된 모든 보호자에게 알림 발송
    public void notifyAllProtectors(User patient, String title, String content) {
        if (patient.getProtectors() != null) {
            patient.getProtectors().forEach(protector ->
                    sendToProtector(protector, title, content)
            );
        }
    }
}