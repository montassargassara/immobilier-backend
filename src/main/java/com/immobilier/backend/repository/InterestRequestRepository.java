package com.immobilier.backend.repository;

import com.immobilier.backend.entity.InterestRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterestRequestRepository extends JpaRepository<InterestRequest, Long> {

    @Query("SELECT i FROM InterestRequest i WHERE i.user.id = :userId ORDER BY i.createdAt DESC")
    List<InterestRequest> findByUserId(@Param("userId") Long userId);

    @Query("SELECT i FROM InterestRequest i WHERE i.ownerUser.id = :ownerId ORDER BY i.createdAt DESC")
    List<InterestRequest> findByOwnerUserId(@Param("ownerId") Long ownerId);

    @Query("SELECT i FROM InterestRequest i WHERE i.user.id = :userId AND i.property.id = :propertyId")
    List<InterestRequest> findByUserAndProperty(@Param("userId") Long userId,
                                                @Param("propertyId") Long propertyId);

    @Query("SELECT i.property.id, COUNT(i) FROM InterestRequest i WHERE i.property.id IN :ids GROUP BY i.property.id")
    List<Object[]> countByPropertyIds(@Param("ids") List<Long> ids);

    /** All leads for a given property (any status). */
    @Query("SELECT i FROM InterestRequest i WHERE i.property.id = :propertyId ORDER BY i.createdAt DESC")
    List<InterestRequest> findByPropertyId(@Param("propertyId") Long propertyId);

    /**
     * Active sibling leads on the same property that are not yet in a terminal state.
     * Used to auto-reject competitors when a lead is converted.
     */
    @Query("""
        SELECT i FROM InterestRequest i
        WHERE i.property.id = :propertyId
          AND i.id <> :excludeId
          AND i.status NOT IN ('REFUSE', 'CONVERTI_VENTE', 'CONVERTI_LOCATION')
        """)
    List<InterestRequest> findActiveLeadsForProperty(@Param("propertyId") Long propertyId,
                                                     @Param("excludeId") Long excludeId);

    /**
     * All active (non-locked) leads for a property — used by scheduler to bulk-refuse
     * when a property is re-opened after a cancelled rental.
     */
    @Query("""
        SELECT i FROM InterestRequest i
        WHERE i.property.id = :propertyId
          AND i.locked = false
        """)
    List<InterestRequest> findUnlockedLeadsForProperty(@Param("propertyId") Long propertyId);
}
