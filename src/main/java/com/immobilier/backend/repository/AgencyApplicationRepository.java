package com.immobilier.backend.repository;

import com.immobilier.backend.entity.AgencyApplication;
import com.immobilier.backend.enums.AgencyApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgencyApplicationRepository extends JpaRepository<AgencyApplication, Long> {

    List<AgencyApplication> findByStatusOrderByCreatedAtDesc(AgencyApplicationStatus status);

    Optional<AgencyApplication> findByUserId(Long userId);

    boolean existsByUserEmail(String email);
}
