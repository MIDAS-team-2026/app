package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.LocationDTO;
import com.example.backend.Model.DTO.SafeZoneDTO;
import com.example.backend.Service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    // 위치 정보 저장
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveLocation(@RequestBody LocationDTO locationDTO) {
        try {
            locationService.saveLocation(locationDTO);
            return ResponseEntity.ok(ApiResponse.success());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "위치 저장 실패"));
        }
    }

    // 안심구역 설정
    @PostMapping("/safezone")
    public ResponseEntity<ApiResponse<Void>> setSafeZone(@RequestBody SafeZoneDTO safeZoneDTO) {
        try {
            locationService.updateSafeZone(safeZoneDTO);
            return ResponseEntity.ok(ApiResponse.success());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "안심구역 설정 실패"));
        }
    }

    // 안심 정보 확인
    @GetMapping("/check-safezone/{userId}")
    public ResponseEntity<ApiResponse<Boolean>> checkSafeZone(@PathVariable Integer userId) {
        try {
            boolean isWithin = locationService.isWithinSafeZone(userId);
            return ResponseEntity.ok(ApiResponse.success(isWithin));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "안심구역 확인 중 오류 발생"));
        }
    }
}