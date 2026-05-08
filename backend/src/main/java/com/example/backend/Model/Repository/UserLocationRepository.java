package com.example.backend.Model.Repository;

import com.example.backend.Model.Entity.location.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLocationRepository extends JpaRepository<UserLocation, Integer> {
}