package com.example.backend.Service;

import com.example.backend.Model.DTO.LocationDTO;
import com.example.backend.Model.Entity.Location;
import com.example.backend.Model.Repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    @Transactional
    public void saveLocation(LocationDTO dto) {
        Location location = new Location();
        // User 객체는 실제 프로젝트의 User 확보 로직에 따라 설정 필요
        location.setLatitude(dto.getLatitude());
        location.setLongitude(dto.getLongitude());
        location.setCreatedAt(Instant.now());
        locationRepository.save(location);
    }

    public Location getLatestLocation(Integer userId) {
        return locationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    // 안심 구역 이탈 판별 로직
    public boolean isWithinSafeZone(Integer userId) {
        Location current = getLatestLocation(userId);
        if (current == null) return true;

        // 임의의 중심점과 반경(예: 500m) - 실제로는 SafeZoneDTO 등에서 가져와야 함
        double centerLat = 37.5665;
        double centerLon = 126.9780;
        double radius = 500.0;

        double distance = calculateDistance(
                current.getLatitude().doubleValue(), current.getLongitude().doubleValue(),
                centerLat, centerLon
        );

        return distance <= radius;
    }

    // 두 좌표 사이의 거리를 계산하는 메서드 (단위: m)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
        dist = Math.acos(dist);
        dist = Math.toDegrees(dist);
        return dist * 60 * 1.1515 * 1609.344;
    }
}