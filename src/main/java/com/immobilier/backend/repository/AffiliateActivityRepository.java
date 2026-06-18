package com.immobilier.backend.repository;

import com.immobilier.backend.entity.AffiliateActivity;
import com.immobilier.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AffiliateActivityRepository extends JpaRepository<AffiliateActivity, Long> {
    
    List<AffiliateActivity> findByAffiliateOrderByActivityDateDesc(User affiliate);
    
    List<AffiliateActivity> findByAffiliateIdAndActivityType(Long affiliateId, String activityType);
    
    @Query("SELECT COUNT(aa) FROM AffiliateActivity aa WHERE aa.affiliate = :affiliate AND aa.activityType = :activityType AND aa.activityDate >= :startDate")
    Long countActivitiesByType(@Param("affiliate") User affiliate, 
                              @Param("activityType") String activityType, 
                              @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT aa.affiliate, COUNT(aa) as activityCount " +
           "FROM AffiliateActivity aa WHERE aa.activityDate >= :startDate " +
           "GROUP BY aa.affiliate ORDER BY COUNT(aa) DESC")
    List<Object[]> getMostActiveAffiliates(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT aa.affiliate, COUNT(aa) as viewCount " +
           "FROM AffiliateActivity aa WHERE aa.activityType = 'VIEW' AND aa.activityDate >= :startDate " +
           "GROUP BY aa.affiliate ORDER BY COUNT(aa) DESC")
    List<Object[]> getAffiliateViewsRanking(@Param("startDate") LocalDateTime startDate);
}
