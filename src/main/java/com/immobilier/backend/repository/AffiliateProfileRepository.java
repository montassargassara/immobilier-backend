package com.immobilier.backend.repository;

import com.immobilier.backend.entity.AffiliateProfile;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.AffiliateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AffiliateProfileRepository extends JpaRepository<AffiliateProfile, Long> {

    Optional<AffiliateProfile> findByUser(User user);

    Optional<AffiliateProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<AffiliateProfile> findByStatus(AffiliateStatus status);

    List<AffiliateProfile> findByStatusOrderByCreatedAtDesc(AffiliateStatus status);

    @Query("SELECT ap FROM AffiliateProfile ap ORDER BY ap.createdAt DESC")
    List<AffiliateProfile> findAllOrderByCreatedAtDesc();

    @Query("SELECT COUNT(ap) FROM AffiliateProfile ap WHERE ap.status = :status")
    long countByStatus(@Param("status") AffiliateStatus status);
}
