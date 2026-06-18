package com.immobilier.backend.service;

import com.immobilier.backend.dto.MonthlyBonusDTO;
import com.immobilier.backend.entity.MonthlyBonus;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.repository.AffiliateTransactionRepository;
import com.immobilier.backend.repository.MonthlyBonusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the monthly affiliate ranking reward cycle.
 *
 * Call {@link #calculateAndSaveMonthlyBonuses(int, int)} at end of each month
 * (e.g. via a scheduled job or Super Admin trigger) to create MonthlyBonus records
 * for the top-3 affiliates of that month.
 *
 * Rewards are fixed TND amounts — they do NOT affect commissionPercentage.
 * Super Admin marks them paid via the /bonuses/{id}/pay endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyBonusService {

    // Configurable fixed rewards in TND (defaults: 2000 / 1500 / 1000)
    @Value("${affiliate.reward.rank1:2000.0}")
    private double rank1Reward;

    @Value("${affiliate.reward.rank2:1500.0}")
    private double rank2Reward;

    @Value("${affiliate.reward.rank3:1000.0}")
    private double rank3Reward;

    private final AffiliateTransactionRepository affiliateTransactionRepository;
    private final MonthlyBonusRepository monthlyBonusRepository;
    private final NotificationService notificationService;

    /**
     * Compute the top-3 affiliates for the given month/year and persist MonthlyBonus records.
     * Idempotent: skips affiliates that already have a record for this period.
     */
    @Transactional
    public List<MonthlyBonusDTO> calculateAndSaveMonthlyBonuses(int month, int year) {
        log.info("Calculating monthly rewards for {}/{}", month, year);

        java.time.LocalDateTime periodStart = java.time.LocalDateTime.of(year, month, 1, 0, 0);
        List<Object[]> ranking = affiliateTransactionRepository.getRankingByAllCommissions(periodStart);

        double[] rewards = {rank1Reward, rank2Reward, rank3Reward};
        List<MonthlyBonusDTO> created = new ArrayList<>();

        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            User affiliate = (User) ranking.get(i)[0];
            int rank = i + 1;

            if (monthlyBonusRepository.existsByAffiliateAndRankingMonthAndRankingYear(affiliate, month, year)) {
                log.debug("Reward already exists for affiliate {} month {}/{}", affiliate.getId(), month, year);
                continue;
            }

            MonthlyBonus bonus = new MonthlyBonus();
            bonus.setAffiliate(affiliate);
            bonus.setRankingMonth(month);
            bonus.setRankingYear(year);
            bonus.setRank(rank);
            bonus.setRewardAmount(rewards[i]);
            bonus.setIsPaid(false);

            monthlyBonusRepository.save(bonus);
            created.add(toDTO(bonus));

            notificationService.create(
                affiliate,
                NotificationType.MONTHLY_BONUS_AWARDED,
                "Récompense mensuelle attribuée",
                String.format("Félicitations ! Vous êtes classé n°%d du mois %d/%d. " +
                    "Vous avez gagné une récompense de %.0f TND.",
                    rank, month, year, rewards[i]),
                bonus.getId()
            );

            log.info("Reward rank {} created for affiliate {} — {} TND", rank, affiliate.getId(), rewards[i]);
        }

        return created;
    }

    /**
     * Mark a reward as paid by Super Admin.
     */
    @Transactional
    public MonthlyBonusDTO markRewardPaid(Long bonusId) {
        MonthlyBonus bonus = monthlyBonusRepository.findById(bonusId)
            .orElseThrow(() -> new RuntimeException("Récompense introuvable"));
        bonus.setIsPaid(true);
        return toDTO(monthlyBonusRepository.save(bonus));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MonthlyBonusDTO> getBonusesForMonth(int month, int year) {
        return monthlyBonusRepository.findByRankingMonthAndRankingYearOrderByRank(month, year)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MonthlyBonusDTO> getBonusHistoryForAffiliate(Long affiliateId) {
        return monthlyBonusRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── DTO converter ─────────────────────────────────────────────────────────

    private MonthlyBonusDTO toDTO(MonthlyBonus b) {
        MonthlyBonusDTO dto = new MonthlyBonusDTO();
        dto.setId(b.getId());
        dto.setAffiliateId(b.getAffiliate().getId());
        dto.setAffiliateName(b.getAffiliate().getFullName());
        dto.setRankingMonth(b.getRankingMonth());
        dto.setRankingYear(b.getRankingYear());
        dto.setRank(b.getRank());
        dto.setRewardAmount(b.getRewardAmount());
        dto.setIsPaid(b.getIsPaid());
        dto.setCreatedAt(b.getCreatedAt());
        return dto;
    }
}
