package com.example.backend.Model.Repository;

import com.example.backend.Model.Entity.location.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Integer> {
    Location findFirstByUserIdOrderByCreatedAtDesc(Integer userId);
}