package com.example.backend.Model.Repository;

import com.example.backend.Model.Entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    Optional<User> findByPatientCode(String patientCode);

    boolean existsByPatientCode(String randomCode);

    List<User> findByTargetPatient_Id(Integer patientId);
}