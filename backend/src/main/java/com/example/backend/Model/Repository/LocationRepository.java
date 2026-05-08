package com.example.backend.Model.Repository;

import com.example.backend.Model.Entity.location.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Integer> {
    Location findFirstByUserIdOrderByCreatedAtDesc(Integer userId);

    // 특정 날짜(하루 동안)의 위치 목록 조회 (시간 오름차순)
    @Query("SELECT l FROM Location l WHERE l.user.id = :userId " +
            "AND l.createdAt >= :startOfDay AND l.createdAt < :endOfDay " +
            "ORDER BY l.createdAt ASC")
    List<Location> findRouteByDate(@Param("userId") Integer userId,
                                   @Param("startOfDay") LocalDateTime startOfDay,
                                   @Param("endOfDay") LocalDateTime endOfDay);
}