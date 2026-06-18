package com.immobilier.backend.repository;

import com.immobilier.backend.entity.MonthlyBonus;
import com.immobilier.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyBonusRepository extends JpaRepository<MonthlyBonus, Long> {

    Optional<MonthlyBonus> findByAffiliateAndRankingMonthAndRankingYear(
            User affiliate, Integer rankingMonth, Integer rankingYear);

    List<MonthlyBonus> findByRankingMonthAndRankingYearOrderByRank(
            Integer rankingMonth, Integer rankingYear);

    List<MonthlyBonus> findByAffiliateIdOrderByCreatedAtDesc(Long affiliateId);

    boolean existsByAffiliateAndRankingMonthAndRankingYear(
            User affiliate, Integer rankingMonth, Integer rankingYear);
}
