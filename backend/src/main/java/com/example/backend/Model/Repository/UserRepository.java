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

    // 특정 환자에 연결된 보호자 목록 (guardian_patient 테이블 역방향)
    List<User> findByPatients_Id(Integer patientId);
}