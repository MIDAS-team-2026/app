package com.example.backend.Model.Repository;

import com.example.backend.Model.Entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Integer> {
    List<Location> findByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(Integer Id, LocalDateTime start);
}