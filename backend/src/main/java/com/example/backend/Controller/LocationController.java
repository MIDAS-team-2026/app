package com.example.backend.Controller;

import com.example.backend.Model.DTO.LocationDTO;
import com.example.backend.Model.Entity.Location;
import com.example.backend.Service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    // 환자의 위도/경도를 실시간으로 저장
    @PostMapping("/save")
    public ResponseEntity<String> saveLocation(@RequestBody LocationDTO locationDTO) {
        locationService.saveLocation(locationDTO);
        return ResponseEntity.ok("위치 정보가 저장되었습니다.");
    }

    // 환자의 가장 최근 위치 확인
    @GetMapping("/latest/{userId}")
    public ResponseEntity<Location> getLatestLocation(@PathVariable Integer userId) {
        return ResponseEntity.ok(locationService.getLatestLocation(userId));
    }

    // 안심 구역 이탈 여부 확인
    @GetMapping("/check-safezone/{userId}")
    public ResponseEntity<Boolean> checkSafeZone(@PathVariable Integer userId) {
        boolean isSafe = locationService.isWithinSafeZone(userId);
        return ResponseEntity.ok(isSafe);
    }
}