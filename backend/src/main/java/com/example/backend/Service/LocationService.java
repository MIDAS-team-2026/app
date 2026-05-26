package com.example.backend.Service;

import com.example.backend.Model.DTO.location.LocationDTO;
import com.example.backend.Model.DTO.location.LocationResponseDTO;
import com.example.backend.Model.DTO.location.SafeZoneDTO;
import com.example.backend.Model.Entity.location.Location;
import com.example.backend.Model.Entity.location.UserLocation;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final UserLocationRepository userLocationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public void saveLocation(LocationDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Location location = new Location();
        location.setUser(user);
        location.setLatitude(dto.getLatitude());
        location.setLongitude(dto.getLongitude());
        locationRepository.save(location);

        UserLocation config = userLocationRepository.findById(dto.getUserId()).orElse(null);
        if (config != null && config.getBaseLatitude() != null) {
            double distance = calculateDistance(
                    dto.getLatitude().doubleValue(), dto.getLongitude().doubleValue(),
                    config.getBaseLatitude().doubleValue(), config.getBaseLongitude().doubleValue()
            );

            // 반경 이탈 시 알림 발송
            if (distance > config.getSafeRadius()) {
                notificationService.notifyAllProtectors(user,
                        "안심구역 이탈 경보",
                        user.getName() + " 님이 설정된 안심구역을 벗어났습니다. 현재 위치를 확인하세요.");
            }
        }
    }

    @Transactional
    public void updateSafeZone(SafeZoneDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // UserLocation(안심구역 설정) 정보 가져오기
        UserLocation config = userLocationRepository.findById(dto.getUserId())
                .orElseGet(() -> {
                    UserLocation newConfig = new UserLocation();
                    newConfig.setUser(user);
                    return newConfig;
                });

        config.setZoneName(dto.getZoneName());
        config.setBaseLatitude(dto.getLatitude());
        config.setBaseLongitude(dto.getLongitude());
        config.setSafeRadius(dto.getRadius());

        userLocationRepository.save(config);
    }

    public boolean isWithinSafeZone(Integer userId) {
        Location current = locationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        UserLocation config = userLocationRepository.findById(userId).orElse(null);

        if (current == null || config == null || config.getBaseLatitude() == null) return true;

        double distance = calculateDistance(
                current.getLatitude().doubleValue(), current.getLongitude().doubleValue(),
                config.getBaseLatitude().doubleValue(), config.getBaseLongitude().doubleValue()
        );

        return distance <= config.getSafeRadius();
    }

    /**
     * 하버사인 공식 (Haversine Formula):
     * 위도(Latitude)와 경도(Longitude) 좌표를 사용하여 구(Sphere) 위에서의 대원 거리를 계산함.
     * 지구의 곡률을 고려하므로, 피타고라스 정리에 비해 GPS 좌표 간 거리 계산 시 오차가 매우 적음.
     * 단위: 미터(m)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371e3; // 지구 반경 (m)
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                        Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);

        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public LocationResponseDTO getCurrentLocation(Integer userId) {
        Location location = locationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (location == null) return null;

        return new LocationResponseDTO(
                location.getLatitude(),
                location.getLongitude(),
                location.getCreatedAt()
        );
    }

    // 2. 일일 동선 조회
    public List<LocationResponseDTO> getDailyRoute(Integer userId, LocalDate date) {
        // 해당 날짜의 시작 시간(00:00:00)과 끝 시간(다음날 00:00:00) 설정
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<Location> routes = locationRepository.findRouteByDate(userId, startOfDay, endOfDay);

        // Entity 리스트를 DTO 리스트로 변환
        return routes.stream()
                .map(loc -> new LocationResponseDTO(
                        loc.getLatitude(),
                        loc.getLongitude(),
                        loc.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }
}