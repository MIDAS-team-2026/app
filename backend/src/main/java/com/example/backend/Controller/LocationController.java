package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.LocationDTO;
import com.example.backend.Model.DTO.SafeZoneDTO;
import com.example.backend.Service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    // 위치 정보 저장
    @PostMapping
    public ResponseEntity<ApiResponse> saveLocation(@RequestBody LocationDTO locationDTO) {
        try {
            locationService.saveLocation(locationDTO);
            return ResponseEntity.ok(ApiResponse.success());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.fail());
        }
    }

    @PostMapping("/safezone")
    public ResponseEntity<ApiResponse> setSafeZone(@RequestBody SafeZoneDTO safeZoneDTO) {
        try {
            locationService.updateSafeZone(safeZoneDTO);
            return ResponseEntity.ok(ApiResponse.success());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.fail());
        }
    }

    @GetMapping("/check-safezone/{userId}")
    public ResponseEntity<Boolean> checkSafeZone(@PathVariable Integer userId) {
        return ResponseEntity.ok(locationService.isWithinSafeZone(userId));
    }
}