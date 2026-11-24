package com.example.backend.Config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    private static final String SERVICE_ACCOUNT_FILE = "firebase-adminsdk.json";

    @PostConstruct
    public void init() {
        ClassPathResource resource = new ClassPathResource(SERVICE_ACCOUNT_FILE);

        // 서비스 계정 키 파일은 저장소에 올리지 않는다(.gitignore).
        // 파일이 없는 개발 환경에서는 오류로 취급하지 않고 초기화를 건너뛴다.
        if (!resource.exists()) {
            log.info("{} 파일이 없어 Firebase 초기화를 건너뜁니다.", SERVICE_ACCOUNT_FILE);
            return;
        }

        try (InputStream serviceAccount = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase 초기화 완료");
            }
        } catch (Exception e) {
            log.warn("Firebase 초기화에 실패했습니다: {}", e.getMessage());
        }
    }
}
