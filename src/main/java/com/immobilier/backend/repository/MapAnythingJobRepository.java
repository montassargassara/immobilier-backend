package com.immobilier.backend.repository;

import com.immobilier.backend.entity.MapAnythingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MapAnythingJobRepository extends JpaRepository<MapAnythingJob, Long> {

    List<MapAnythingJob> findAllByPropertyIdOrderByCreatedAtDesc(Long propertyId);
}
