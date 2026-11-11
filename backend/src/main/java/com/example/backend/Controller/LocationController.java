package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.location.LocationDTO;
import com.example.backend.Model.DTO.location.LocationResponseDTO;
import com.example.backend.Model.DTO.location.SafeZoneDTO;
import com.example.backend.Service.LocationService;
import com.example.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final UserService userService;

    // 위치 정보 저장
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveLocation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody LocationDTO locationDTO) {

        userService.validatePatientAccess(authorizationHeader, locationDTO.getUserId());

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
    public ResponseEntity<ApiResponse<Void>> setSafeZone(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody SafeZoneDTO safeZoneDTO) {

        userService.validatePatientAccess(authorizationHeader, safeZoneDTO.getUserId());

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
    public ResponseEntity<ApiResponse<Boolean>> checkSafeZone(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId) {

        userService.validatePatientAccess(authorizationHeader, userId);

        try {
            boolean isWithin = locationService.isWithinSafeZone(userId);
            return ResponseEntity.ok(ApiResponse.success(isWithin));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(500, "안심구역 확인 중 오류 발생"));
        }
    }

    // 현재 위치 조회
    @GetMapping("/current/{userId}")
    public ResponseEntity<ApiResponse<LocationResponseDTO>> getCurrentLocation(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId) {

        userService.validatePatientAccess(authorizationHeader, userId);

        LocationResponseDTO location = locationService.getCurrentLocation(userId);

        if (location != null) {
            return ResponseEntity.ok(ApiResponse.success(location));
        } else {
            return ResponseEntity.ok(ApiResponse.fail(404, "위치 정보가 없습니다."));
        }
    }

    // 일일 이동 동선 조회
    @GetMapping("/route/{userId}")
    public ResponseEntity<ApiResponse<List<LocationResponseDTO>>> getDailyRoute(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        userService.validatePatientAccess(authorizationHeader, userId);

        // (?date=2026-05-09 형식으로 파라미터 전달)
        List<LocationResponseDTO> route = locationService.getDailyRoute(userId, date);
        return ResponseEntity.ok(ApiResponse.success(route));
    }
}