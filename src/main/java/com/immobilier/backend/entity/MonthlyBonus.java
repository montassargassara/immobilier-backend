package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persists the fixed monetary reward awarded to top-3 affiliates each ranking month.
 *
 * Rank 1 → 2 000 TND   Rank 2 → 1 500 TND   Rank 3 → 1 000 TND
 *
 * Rewards are one-time — they do NOT affect commissionPercentage on future sales.
 * Super Admin marks them as paid separately.
 */
@Entity
@Table(name = "monthly_bonuses",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_bonus_affiliate_month",
        columnNames = {"affiliate_id", "ranking_month", "ranking_year"}
    ),
    indexes = {
        @Index(name = "idx_bonus_affiliate",      columnList = "affiliate_id"),
        @Index(name = "idx_bonus_ranking_period", columnList = "ranking_month, ranking_year"),
        @Index(name = "idx_bonus_is_paid",        columnList = "is_paid")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyBonus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private User affiliate;

    // Month that was ranked (1–12)
    @Column(name = "ranking_month", nullable = false)
    private Integer rankingMonth;

    @Column(name = "ranking_year", nullable = false)
    private Integer rankingYear;

    // Position in that month's leaderboard (1, 2, or 3)
    @Column(nullable = false)
    private Integer rank;

    // Fixed TND reward for this rank (2000 / 1500 / 1000)
    @Column(name = "reward_amount", nullable = false)
    private Double rewardAmount;

    // True once Super Admin marks the reward as paid
    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
