package com.example.backend.Model.Entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Integer> {
    List<Location> findByUser_UserIdAndCreatedAtAfterOrderByCreatedAtDesc(Integer userId, LocalDateTime start);
}