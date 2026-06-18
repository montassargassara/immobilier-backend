package com.immobilier.backend.repository;

import com.immobilier.backend.entity.VirtualTourScene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VirtualTourSceneRepository extends JpaRepository<VirtualTourScene, Long> {
    List<VirtualTourScene> findByTourIdOrderBySceneIndexAsc(Long tourId);
    void deleteByTourId(Long tourId);
}
