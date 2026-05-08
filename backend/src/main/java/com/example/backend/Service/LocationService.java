package com.example.backend.Service;

import com.example.backend.Model.DTO.LocationDTO;
import com.example.backend.Model.DTO.SafeZoneDTO;
import com.example.backend.Model.Entity.location.Location;
import com.example.backend.Model.Entity.location.UserLocation;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final UserLocationRepository userLocationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void saveLocation(LocationDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Location location = new Location();
        location.setUser(user);
        location.setLatitude(dto.getLatitude());
        location.setLongitude(dto.getLongitude());

        locationRepository.save(location);
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
}