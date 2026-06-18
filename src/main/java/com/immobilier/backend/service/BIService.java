package com.immobilier.backend.service;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.AffiliateTransactionRepository;
import com.immobilier.backend.repository.ClientInfoRepository;
import com.immobilier.backend.repository.PropertyRepository;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BIService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final AffiliateTransactionRepository affiliateTransactionRepository;
    private final ClientInfoRepository clientInfoRepository;
    private final com.immobilier.backend.repository.CommissionRepository commissionRepository;
    private final com.immobilier.backend.repository.AffiliateCustomerRelationRepository affiliateCustomerRelationRepository;
    private final SecurityUtils securityUtils;
    private final RevenueCalculator revenueCalculator;

    private static final DateTimeFormatter MONTH_LABEL_FMT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH);

    // ── KPIs ─────────────────────────────────────────────────────────────────

    public BIKpiDTO getKpis() {
        return securityUtils.isSuperAdmin() ? getKpisGlobal() : getKpisForAgency(securityUtils.getCurrentUserId());
    }

    private BIKpiDTO getKpisGlobal() {
        LocalDateTime now           = LocalDateTime.now();
        LocalDateTime startOfMonth  = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startPrevMonth = startOfMonth.minusMonths(1);
        LocalDateTime sixMonthsAgo  = now.minusMonths(6);

        long total      = propertyRepository.count();
        long disponible = propertyRepository.countByStatut("DISPONIBLE");
        long vendu      = propertyRepository.countByStatut("VENDU");
        long loue       = propertyRepository.countByStatut("LOUE");

        long agencies   = userRepository.countByRole(RoleType.ADMIN);
        long affiliates = userRepository.countByRole(RoleType.AFFILIATE);
        long clients    = userRepository.countByRole(RoleType.CLIENT_PUBLIC);

        double totalRevenue  = Optional.ofNullable(propertyRepository.calculateTotalRevenue()).orElse(0.0);
        double currRevenue   = Optional.ofNullable(propertyRepository.calculateMonthlyRevenue(startOfMonth)).orElse(0.0);
        double prevRevenue   = Optional.ofNullable(
                propertyRepository.calculateRevenueByMonth(startPrevMonth.getMonthValue(), startPrevMonth.getYear())
        ).orElse(0.0);

        double totalCommissions = Optional.ofNullable(affiliateTransactionRepository.getTotalCommissions()).orElse(0.0);
        double currCommissions  = Optional.ofNullable(affiliateTransactionRepository.getTotalCommissionsSince(startOfMonth)).orElse(0.0);
        double prevCommissions  = Optional.ofNullable(affiliateTransactionRepository.getTotalCommissionsSince(startPrevMonth)).orElse(0.0)
                                  - currCommissions;

        long currSales = propertyRepository.countVenduBetween(startOfMonth, now)
                       + propertyRepository.countLoueBetween(startOfMonth, now);
        long prevSales = propertyRepository.countVenduBetween(startPrevMonth, startOfMonth)
                       + propertyRepository.countLoueBetween(startPrevMonth, startOfMonth);

        long stagnant = propertyRepository.countStagnantProperties(sixMonthsAgo);

        return buildKpiDTO(total, disponible, vendu, loue, agencies, affiliates, clients,
                totalRevenue, currRevenue, prevRevenue, totalCommissions, currCommissions,
                prevCommissions, currSales, prevSales, stagnant);
    }

    private BIKpiDTO getKpisForAgency(Long adminId) {
        LocalDateTime now           = LocalDateTime.now();
        LocalDateTime startOfMonth  = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startPrevMonth = startOfMonth.minusMonths(1);
        LocalDateTime sixMonthsAgo  = now.minusMonths(6);

        long total      = propertyRepository.countByAgencyAdmin(adminId);
        long disponible = propertyRepository.countAvailableByAgencyAdmin(adminId);
        long vendu      = propertyRepository.countSoldByAgencyAdmin(adminId);
        long loue       = propertyRepository.countRentedByAgencyAdmin(adminId);

        long affiliates = affiliateTransactionRepository.countDistinctAffiliatesByAgencyAdmin(adminId);
        long clients    = clientInfoRepository.countByAgencyAdminId(adminId);

        double totalRevenue  = Optional.ofNullable(propertyRepository.calculateTotalRevenueByAgencyAdmin(adminId)).orElse(0.0);
        double currRevenue   = Optional.ofNullable(propertyRepository.calculateMonthlyRevenueByAgencyAdmin(adminId, startOfMonth)).orElse(0.0);
        double prevRevenue   = Optional.ofNullable(
                propertyRepository.calculateRevenueByMonthByAgencyAdmin(adminId, startPrevMonth.getMonthValue(), startPrevMonth.getYear())
        ).orElse(0.0);

        double totalCommissions = Optional.ofNullable(affiliateTransactionRepository.getTotalCommissionsByAgencyAdmin(adminId)).orElse(0.0);
        double currCommissions  = Optional.ofNullable(affiliateTransactionRepository.getTotalCommissionsSinceByAgencyAdmin(adminId, startOfMonth)).orElse(0.0);
        double prevCommissions  = Optional.ofNullable(affiliateTransactionRepository.getTotalCommissionsSinceByAgencyAdmin(adminId, startPrevMonth)).orElse(0.0)
                                  - currCommissions;

        long currSales = propertyRepository.countVenduBetweenByAgencyAdmin(adminId, startOfMonth, now)
                       + propertyRepository.countLoueBetweenByAgencyAdmin(adminId, startOfMonth, now);
        long prevSales = propertyRepository.countVenduBetweenByAgencyAdmin(adminId, startPrevMonth, startOfMonth)
                       + propertyRepository.countLoueBetweenByAgencyAdmin(adminId, startPrevMonth, startOfMonth);

        long stagnant = propertyRepository.countStagnantByAgencyAdmin(adminId, sixMonthsAgo);

        // agencyCount is 0 for ADMIN — frontend hides that KPI card
        return buildKpiDTO(total, disponible, vendu, loue, 0, affiliates, clients,
                totalRevenue, currRevenue, prevRevenue, totalCommissions, currCommissions,
                prevCommissions, currSales, prevSales, stagnant);
    }

    private BIKpiDTO buildKpiDTO(long total, long disponible, long vendu, long loue,
                                  long agencies, long affiliates, long clients,
                                  double totalRevenue, double currRevenue, double prevRevenue,
                                  double totalCommissions, double currCommissions, double prevCommissions,
                                  long currSales, long prevSales, long stagnant) {
        double revenueTrend     = trend(currRevenue, prevRevenue);
        double salesTrend       = trend(currSales, prevSales);
        double commissionsTrend = trend(currCommissions, Math.max(prevCommissions, 0));
        double conversionRate   = total > 0 ? round1((vendu + loue) * 100.0 / total) : 0;

        return BIKpiDTO.builder()
                .totalProperties(total)
                .disponibleCount(disponible)
                .venduCount(vendu)
                .loueCount(loue)
                .agencyCount(agencies)
                .affiliateCount(affiliates)
                .clientCount(clients)
                .totalRevenue(totalRevenue)
                .currentMonthRevenue(currRevenue)
                .previousMonthRevenue(prevRevenue)
                .totalCommissions(totalCommissions)
                .currentMonthCommissions(currCommissions)
                .conversionRate(conversionRate)
                .revenueTrend(round1(revenueTrend))
                .salesTrend(round1(salesTrend))
                .commissionsTrend(round1(commissionsTrend))
                .currentMonthSales(currSales)
                .previousMonthSales(prevSales)
                .stagnantProperties(stagnant)
                .build();
    }

    // ── Revenue breakdown (global / agency / super-admin-own, gross & net) ────

    public BIRevenueBreakdownDTO getRevenueBreakdown() {
        return securityUtils.isSuperAdmin()
                ? revenueCalculator.global()
                : revenueCalculator.forAgency(securityUtils.getCurrentUserId());
    }

    // ── Affiliate network impact (real relations, no fake data) ──────────────

    public BIAffiliateImpactDTO getAffiliateImpact() {
        long clients  = affiliateCustomerRelationRepository.countAll();
        long sales    = affiliateCustomerRelationRepository.countByTransactionType("SALE");
        double revenue = orZero(affiliateCustomerRelationRepository.getTotalRevenueViaAffiliates());
        double paid    = orZero(affiliateTransactionRepository.getTotalPaidCommissions());
        double pending = orZero(affiliateTransactionRepository.getTotalUnpaidCommissions());

        return BIAffiliateImpactDTO.builder()
                .clientsBrought(clients)
                .salesViaAffiliates(sales)
                .revenueViaAffiliates(round1(revenue))
                .commissionsPaid(round1(paid))
                .commissionsPending(round1(pending))
                .build();
    }

    // ── Commission intelligence (affiliate + agency + staff) ─────────────────

    public BICommissionDTO getCommissionBreakdown() {
        return securityUtils.isSuperAdmin()
                ? commissionBreakdownGlobal()
                : commissionBreakdownForAgency(securityUtils.getCurrentUserId());
    }

    private BICommissionDTO commissionBreakdownGlobal() {
        double affTotal   = orZero(affiliateTransactionRepository.getTotalCommissions());
        double affPaid    = orZero(affiliateTransactionRepository.getTotalPaidCommissions());
        double affPending = orZero(affiliateTransactionRepository.getTotalUnpaidCommissions());
        long   affPendCnt = affiliateTransactionRepository.countUnpaidTransactions();
        long   affPaidCnt = affiliateTransactionRepository.countByIsPaid(true);

        double agTotal    = orZero(commissionRepository.sumByType("AGENCY"));
        double agPending  = orZero(commissionRepository.sumByTypeAndStatus("AGENCY", "PENDING"));
        double agPaid     = orZero(commissionRepository.sumByTypeAndStatus("AGENCY", "PAID"));
        long   agPendCnt  = commissionRepository.countByTypeAndStatus("AGENCY", "PENDING");
        long   agPaidCnt  = commissionRepository.countByTypeAndStatus("AGENCY", "PAID");

        double stTotal    = orZero(commissionRepository.sumByType("STAFF"));
        double stPending  = orZero(commissionRepository.sumByTypeAndStatus("STAFF", "PENDING"));
        double stPaid     = orZero(commissionRepository.sumByTypeAndStatus("STAFF", "PAID"));
        long   stPendCnt  = commissionRepository.countByTypeAndStatus("STAFF", "PENDING");
        long   stPaidCnt  = commissionRepository.countByTypeAndStatus("STAFF", "PAID");

        return assembleCommissionDTO("GLOBAL",
                affTotal, agTotal, stTotal,
                affPending, agPending, stPending, affPendCnt + agPendCnt + stPendCnt,
                affPaid, agPaid, stPaid, affPaidCnt + agPaidCnt + stPaidCnt);
    }

    private BICommissionDTO commissionBreakdownForAgency(Long adminId) {
        // Affiliate commissions earned on this agency's properties
        double affTotal   = orZero(affiliateTransactionRepository.getTotalCommissionsByAgencyAdmin(adminId));
        double affPending = orZero(affiliateTransactionRepository.getTotalUnpaidCommissionsByAgencyAdmin(adminId));
        double affPaid    = Math.max(0, affTotal - affPending);
        long   affPendCnt = affiliateTransactionRepository.countUnpaidTransactionsByAgencyAdmin(adminId);

        // Agency-direct commissions this admin (agency) earned from Super Admin shared properties
        double agTotal    = orZero(commissionRepository.sumByBeneficiaryAndStatus(adminId, "PENDING"))
                          + orZero(commissionRepository.sumByBeneficiaryAndStatus(adminId, "PAID"));
        double agPending  = orZero(commissionRepository.sumByBeneficiaryAndStatus(adminId, "PENDING"));
        double agPaid     = orZero(commissionRepository.sumByBeneficiaryAndStatus(adminId, "PAID"));

        // Staff commissions owed to this agency's own staff
        double stPending  = orZero(commissionRepository.sumStaffByAgencyAdminAndStatus(adminId, "PENDING"));
        double stPaid     = orZero(commissionRepository.sumStaffByAgencyAdminAndStatus(adminId, "PAID"));
        double stTotal    = stPending + stPaid;

        return assembleCommissionDTO("AGENCY",
                affTotal, agTotal, stTotal,
                affPending, agPending, stPending, affPendCnt,
                affPaid, agPaid, stPaid, 0);
    }

    private BICommissionDTO assembleCommissionDTO(String scope,
            double affTotal, double agTotal, double stTotal,
            double affPending, double agPending, double stPending, long pendingCount,
            double affPaid, double agPaid, double stPaid, long paidCount) {
        return BICommissionDTO.builder()
                .scope(scope)
                .affiliateTotal(round1(affTotal))
                .agencyTotal(round1(agTotal))
                .staffTotal(round1(stTotal))
                .grandTotal(round1(affTotal + agTotal + stTotal))
                .affiliatePending(round1(affPending))
                .agencyPending(round1(agPending))
                .staffPending(round1(stPending))
                .pendingTotal(round1(affPending + agPending + stPending))
                .pendingCount(pendingCount)
                .affiliatePaid(round1(affPaid))
                .agencyPaid(round1(agPaid))
                .staffPaid(round1(stPaid))
                .paidTotal(round1(affPaid + agPaid + stPaid))
                .paidCount(paidCount)
                .build();
    }

    // ── Staff ranking (top-performing internal staff) ────────────────────────

    public List<BIStaffRankDTO> getStaffRanking(int limit) {
        List<Object[]> raw = securityUtils.isSuperAdmin()
                ? commissionRepository.staffRankingGlobal(PageRequest.of(0, limit))
                : commissionRepository.staffRankingByAgency(securityUtils.getCurrentUserId(), PageRequest.of(0, limit));

        List<BIStaffRankDTO> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Object[] r = raw.get(i);
            User u = (User) r[0];
            result.add(BIStaffRankDTO.builder()
                    .id(u.getId())
                    .name(u.getPrenom() + " " + u.getNom())
                    .email(u.getEmail())
                    .role(u.getRole() != null ? u.getRole().name() : "")
                    .salesCount(((Number) r[1]).longValue())
                    .totalCommission(r[2] != null ? round1(((Number) r[2]).doubleValue()) : 0)
                    .pendingCommission(r[3] != null ? round1(((Number) r[3]).doubleValue()) : 0)
                    .rank(i + 1)
                    .build());
        }
        return result;
    }

    private double orZero(Double v) { return v != null ? v : 0.0; }

    // ── Trends (12 months) ────────────────────────────────────────────────────

    public BITrendDTO getTrends() {
        return securityUtils.isSuperAdmin() ? getTrendsGlobal() : getTrendsForAgency(securityUtils.getCurrentUserId());
    }

    private BITrendDTO getTrendsGlobal() {
        LocalDateTime since = startOf12MonthsAgo();
        List<String> months = buildMonthLabels(since);
        List<LocalDateTime> monthStarts = buildMonthStarts(since);

        Map<String, Long>   salesMap   = toLongMap(propertyRepository.getMonthlySalesSince(since));
        Map<String, Long>   rentalsMap = toLongMap(propertyRepository.getMonthlyRentalsSince(since));
        Map<String, Double> revenueMap = toDoubleMap(propertyRepository.getMonthlyRevenueSince(since));
        Map<String, Double> commMap    = toDoubleMap(affiliateTransactionRepository.getMonthlyCommissionsSince(since));
        Map<String, Long>   clientMap  = toLongMap(
                userRepository.countNewUsersByRoleByMonth(RoleType.CLIENT_PUBLIC, since));

        return assembleTrendDTO(months, monthStarts, salesMap, rentalsMap, revenueMap, commMap, clientMap);
    }

    private BITrendDTO getTrendsForAgency(Long adminId) {
        LocalDateTime since = startOf12MonthsAgo();
        List<String> months = buildMonthLabels(since);
        List<LocalDateTime> monthStarts = buildMonthStarts(since);

        Map<String, Long>   salesMap   = toLongMap(propertyRepository.getMonthlySalesByAgencyAdminSince(adminId, since));
        Map<String, Long>   rentalsMap = toLongMap(propertyRepository.getMonthlyRentalsByAgencyAdminSince(adminId, since));
        Map<String, Double> revenueMap = toDoubleMap(propertyRepository.getMonthlyRevenueByAgencyAdminSince(adminId, since));
        Map<String, Double> commMap    = toDoubleMap(affiliateTransactionRepository.getMonthlyCommissionsSinceByAgencyAdmin(adminId, since));
        // newClients for ADMIN = clients added to this agency's CRM each month — not a per-month query we have;
        // return zeros (no CLIENT_PUBLIC growth curve for a single agency)
        Map<String, Long> clientMap = Collections.emptyMap();

        return assembleTrendDTO(months, monthStarts, salesMap, rentalsMap, revenueMap, commMap, clientMap);
    }

    private BITrendDTO assembleTrendDTO(List<String> months, List<LocalDateTime> monthStarts,
                                         Map<String, Long> salesMap, Map<String, Long> rentalsMap,
                                         Map<String, Double> revenueMap, Map<String, Double> commMap,
                                         Map<String, Long> clientMap) {
        List<Long>   salesCounts  = new ArrayList<>();
        List<Long>   rentalCounts = new ArrayList<>();
        List<Double> revenues     = new ArrayList<>();
        List<Double> commissions  = new ArrayList<>();
        List<Long>   newClients   = new ArrayList<>();

        for (LocalDateTime ms : monthStarts) {
            String key = ms.getYear() + "-" + ms.getMonthValue();
            salesCounts.add(salesMap.getOrDefault(key, 0L));
            rentalCounts.add(rentalsMap.getOrDefault(key, 0L));
            revenues.add(revenueMap.getOrDefault(key, 0.0));
            commissions.add(commMap.getOrDefault(key, 0.0));
            newClients.add(clientMap.getOrDefault(key, 0L));
        }

        return BITrendDTO.builder()
                .months(months)
                .salesCounts(salesCounts)
                .rentalCounts(rentalCounts)
                .revenues(revenues)
                .commissions(commissions)
                .newClients(newClients)
                .build();
    }

    // ── Top Cities ────────────────────────────────────────────────────────────

    public List<BITopCityDTO> getTopCities(int limit) {
        return securityUtils.isSuperAdmin()
                ? getTopCitiesGlobal(limit)
                : getTopCitiesForAgency(securityUtils.getCurrentUserId(), limit);
    }

    private List<BITopCityDTO> getTopCitiesGlobal(int limit) {
        List<Object[]> soldRaw   = propertyRepository.getTopCitiesBySales(PageRequest.of(0, limit));
        List<Object[]> activeRaw = propertyRepository.getTopCitiesByActive(PageRequest.of(0, limit));
        return mergeTopCities(soldRaw, activeRaw);
    }

    private List<BITopCityDTO> getTopCitiesForAgency(Long adminId, int limit) {
        List<Object[]> soldRaw   = propertyRepository.getTopCitiesBySalesByAgencyAdmin(adminId, PageRequest.of(0, limit));
        List<Object[]> activeRaw = propertyRepository.getTopCitiesByActiveByAgencyAdmin(adminId, PageRequest.of(0, limit));
        return mergeTopCities(soldRaw, activeRaw);
    }

    private List<BITopCityDTO> mergeTopCities(List<Object[]> soldRaw, List<Object[]> activeRaw) {
        Map<String, Long> activeMap = new HashMap<>();
        for (Object[] r : activeRaw) {
            String key = r[0] + "|" + r[1];
            activeMap.put(key, ((Number) r[2]).longValue());
        }
        return soldRaw.stream().map(r -> {
            String city    = (String) r[0];
            String country = (String) r[1];
            String key     = city + "|" + country;
            return BITopCityDTO.builder()
                    .city(city)
                    .country(country)
                    .soldCount(((Number) r[2]).longValue())
                    .totalRevenue(r[3] != null ? ((Number) r[3]).doubleValue() : 0)
                    .activeCount(activeMap.getOrDefault(key, 0L))
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Type Breakdown ────────────────────────────────────────────────────────

    public List<BITypeBreakdownDTO> getTypeBreakdown() {
        List<Object[]> raw = securityUtils.isSuperAdmin()
                ? propertyRepository.getTypeStatsByStatus()
                : propertyRepository.getTypeStatsByStatusByAgencyAdmin(securityUtils.getCurrentUserId());
        return buildTypeBreakdown(raw);
    }

    private List<BITypeBreakdownDTO> buildTypeBreakdown(List<Object[]> raw) {
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (Object[] row : raw) {
            String type   = (String) row[0];
            long   count  = ((Number) row[1]).longValue();
            String statut = (String) row[2];
            grouped.putIfAbsent(type, new long[3]);
            grouped.get(type)[0] += count;
            if ("VENDU".equals(statut) || "LOUE".equals(statut)) grouped.get(type)[1] += count;
            if ("DISPONIBLE".equals(statut))                       grouped.get(type)[2] += count;
        }
        long grandTotal = grouped.values().stream().mapToLong(a -> a[0]).sum();

        return grouped.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0]).reversed())
                .map(e -> BITypeBreakdownDTO.builder()
                        .type(e.getKey())
                        .totalCount(e.getValue()[0])
                        .soldCount(e.getValue()[1])
                        .activeCount(e.getValue()[2])
                        .percentage(grandTotal > 0 ? round1(e.getValue()[0] * 100.0 / grandTotal) : 0)
                        .build())
                .collect(Collectors.toList());
    }

    // ── Agency Ranking ────────────────────────────────────────────────────────

    public List<BIAgencyRankDTO> getAgencyRanking(int limit) {
        // Only SUPER_ADMIN sees the agency ranking — ADMIN gets an empty list (panel is hidden in UI)
        if (!securityUtils.isSuperAdmin()) return Collections.emptyList();

        List<Object[]> soldRaw   = propertyRepository.getAgencyRankingBySales(PageRequest.of(0, limit));
        List<Object[]> activeRaw = propertyRepository.getActiveCountByAgency();

        Map<Long, Long> activeMap = new HashMap<>();
        for (Object[] r : activeRaw) {
            activeMap.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        List<BIAgencyRankDTO> result = new ArrayList<>();
        for (int i = 0; i < soldRaw.size(); i++) {
            Object[] r = soldRaw.get(i);
            long id = ((Number) r[0]).longValue();
            result.add(BIAgencyRankDTO.builder()
                    .id(id)
                    .agencyName(r[1] + " " + r[2])
                    .email((String) r[3])
                    .propertiesSold(((Number) r[4]).longValue())
                    .revenue(r[5] != null ? ((Number) r[5]).doubleValue() : 0)
                    .propertiesActive(activeMap.getOrDefault(id, 0L))
                    .rank(i + 1)
                    .build());
        }
        return result;
    }

    // ── Affiliate Ranking ─────────────────────────────────────────────────────

    public List<BIAffiliateRankDTO> getAffiliateRanking(int limit) {
        LocalDateTime since = LocalDateTime.now().minusMonths(12);
        List<Object[]> raw = securityUtils.isSuperAdmin()
                ? affiliateTransactionRepository.getAffiliateRanking(since)
                : affiliateTransactionRepository.getAffiliateRankingByAgencyAdmin(securityUtils.getCurrentUserId(), since);

        List<BIAffiliateRankDTO> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, raw.size()); i++) {
            Object[] r = raw.get(i);
            User affiliate = (User) r[0];
            result.add(BIAffiliateRankDTO.builder()
                    .id(affiliate.getId())
                    .name(affiliate.getPrenom() + " " + affiliate.getNom())
                    .email(affiliate.getEmail())
                    .salesCompleted(((Number) r[1]).longValue())
                    .totalCommissions(r[2] != null ? ((Number) r[2]).doubleValue() : 0)
                    .rank(i + 1)
                    .build());
        }
        return result;
    }

    // ── Rental KPIs ───────────────────────────────────────────────────────────

    public BILocationKpiDTO getRentalKpis() {
        return securityUtils.isSuperAdmin()
                ? getRentalKpisGlobal()
                : getRentalKpisForAgency(securityUtils.getCurrentUserId());
    }

    private BILocationKpiDTO getRentalKpisGlobal() {
        LocalDateTime now            = LocalDateTime.now();
        LocalDateTime startOfMonth   = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth     = startOfMonth.plusMonths(1).minusNanos(1);
        LocalDateTime startPrevMonth = startOfMonth.minusMonths(1);
        LocalDateTime endPrevMonth   = startOfMonth.minusNanos(1);
        LocalDateTime future30       = now.plusDays(30);

        long activeRentals        = propertyRepository.countByStatut("LOUE");
        long expiringIn30Days     = propertyRepository.countExpiringRentals(now, future30);
        long totalLocProps        = propertyRepository.countAllLocationProperties();
        double currMonthRevenue   = Optional.ofNullable(propertyRepository.getRentalRevenueForMonth(startOfMonth, endOfMonth)).orElse(0.0);
        double prevMonthRevenue   = Optional.ofNullable(propertyRepository.getRentalRevenueForMonth(startPrevMonth, endPrevMonth)).orElse(0.0);
        double avg12m             = computeAvgMonthlyRentalRevenue(null);

        return buildRentalKpiDTO(activeRentals, expiringIn30Days, totalLocProps, currMonthRevenue, prevMonthRevenue, avg12m);
    }

    private BILocationKpiDTO getRentalKpisForAgency(Long adminId) {
        LocalDateTime now            = LocalDateTime.now();
        LocalDateTime startOfMonth   = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth     = startOfMonth.plusMonths(1).minusNanos(1);
        LocalDateTime startPrevMonth = startOfMonth.minusMonths(1);
        LocalDateTime endPrevMonth   = startOfMonth.minusNanos(1);
        LocalDateTime future30       = now.plusDays(30);

        long activeRentals        = propertyRepository.countRentedByAgencyAdmin(adminId);
        long expiringIn30Days     = propertyRepository.countExpiringRentalsByAgency(adminId, now, future30);
        long totalLocProps        = propertyRepository.countAllLocationPropertiesByAgency(adminId);
        double currMonthRevenue   = Optional.ofNullable(propertyRepository.getRentalRevenueForMonthByAgency(adminId, startOfMonth, endOfMonth)).orElse(0.0);
        double prevMonthRevenue   = Optional.ofNullable(propertyRepository.getRentalRevenueForMonthByAgency(adminId, startPrevMonth, endPrevMonth)).orElse(0.0);
        double avg12m             = computeAvgMonthlyRentalRevenue(adminId);

        return buildRentalKpiDTO(activeRentals, expiringIn30Days, totalLocProps, currMonthRevenue, prevMonthRevenue, avg12m);
    }

    private BILocationKpiDTO buildRentalKpiDTO(long activeRentals, long expiringIn30Days,
                                                long totalLocProps, double currRevenue,
                                                double prevRevenue, double avg12m) {
        double trend             = round1(trend(currRevenue, prevRevenue));
        double annualProjected   = round1(currRevenue * 12);
        double occupancyRate     = totalLocProps > 0 ? round1(activeRentals * 100.0 / totalLocProps) : 0;

        return BILocationKpiDTO.builder()
                .activeRentals(activeRentals)
                .expiringIn30Days(expiringIn30Days)
                .totalLocationProperties(totalLocProps)
                .currentMonthRevenue(round1(currRevenue))
                .previousMonthRevenue(round1(prevRevenue))
                .revenueTrend(trend)
                .annualProjectedRevenue(annualProjected)
                .averageMonthlyRevenue(avg12m)
                .occupancyRate(occupancyRate)
                .build();
    }

    private double computeAvgMonthlyRentalRevenue(Long adminId) {
        LocalDateTime since  = startOf12MonthsAgo();
        LocalDateTime cursor = since;
        LocalDateTime now    = LocalDateTime.now();
        double total         = 0.0;
        int count            = 0;
        while (!cursor.isAfter(now)) {
            LocalDateTime end = cursor.plusMonths(1).minusNanos(1);
            Double rev = adminId == null
                    ? propertyRepository.getRentalRevenueForMonth(cursor, end)
                    : propertyRepository.getRentalRevenueForMonthByAgency(adminId, cursor, end);
            total += Optional.ofNullable(rev).orElse(0.0);
            count++;
            cursor = cursor.plusMonths(1);
        }
        return count > 0 ? round1(total / count) : 0;
    }

    // ── Rental Trends ─────────────────────────────────────────────────────────

    public BILocationTrendDTO getRentalTrends() {
        return securityUtils.isSuperAdmin()
                ? getRentalTrendsGlobal()
                : getRentalTrendsForAgency(securityUtils.getCurrentUserId());
    }

    private BILocationTrendDTO getRentalTrendsGlobal() {
        LocalDateTime since       = startOf12MonthsAgo();
        List<String> months       = buildMonthLabels(since);
        List<LocalDateTime> starts = buildMonthStarts(since);

        List<Double> revenues = computeMonthlyRentalRevenues(starts, null);

        List<Object[]> durRaw = propertyRepository.getRentalDurationBreakdown();
        List<Object[]> agRaw  = propertyRepository.getAgencyRentalRevenueRanking(PageRequest.of(0, 8));

        return assembleRentalTrendDTO(months, revenues, durRaw, agRaw);
    }

    private BILocationTrendDTO getRentalTrendsForAgency(Long adminId) {
        LocalDateTime since       = startOf12MonthsAgo();
        List<String> months       = buildMonthLabels(since);
        List<LocalDateTime> starts = buildMonthStarts(since);

        List<Double> revenues = computeMonthlyRentalRevenues(starts, adminId);

        List<Object[]> durRaw = propertyRepository.getRentalDurationBreakdownByAgency(adminId);

        return BILocationTrendDTO.builder()
                .months(months)
                .monthlyRevenues(revenues)
                .durationLabels(List.of("Court (1-3m)", "Moyen (4-11m)", "Long (12m)", "Très long (>12m)"))
                .durationCounts(buildDurationBuckets(durRaw))
                .agencyNames(Collections.emptyList())
                .agencyRevenues(Collections.emptyList())
                .build();
    }

    private List<Double> computeMonthlyRentalRevenues(List<LocalDateTime> starts, Long adminId) {
        List<Double> list = new ArrayList<>();
        for (LocalDateTime ms : starts) {
            LocalDateTime end = ms.plusMonths(1).minusNanos(1);
            Double rev = adminId == null
                    ? propertyRepository.getRentalRevenueForMonth(ms, end)
                    : propertyRepository.getRentalRevenueForMonthByAgency(adminId, ms, end);
            list.add(round1(Optional.ofNullable(rev).orElse(0.0)));
        }
        return list;
    }

    private BILocationTrendDTO assembleRentalTrendDTO(List<String> months, List<Double> revenues,
                                                       List<Object[]> durRaw, List<Object[]> agRaw) {
        List<String> durationLabels = List.of("Court (1-3m)", "Moyen (4-11m)", "Long (12m)", "Très long (>12m)");
        List<Long>   durationCounts = buildDurationBuckets(durRaw);

        List<String> agencyNames    = new ArrayList<>();
        List<Double> agencyRevenues = new ArrayList<>();
        for (Object[] r : agRaw) {
            agencyNames.add(r[1] + " " + r[2]);
            agencyRevenues.add(r[3] != null ? ((Number) r[3]).doubleValue() : 0.0);
        }

        return BILocationTrendDTO.builder()
                .months(months)
                .monthlyRevenues(revenues)
                .durationLabels(durationLabels)
                .durationCounts(durationCounts)
                .agencyNames(agencyNames)
                .agencyRevenues(agencyRevenues)
                .build();
    }

    private List<Long> buildDurationBuckets(List<Object[]> durRaw) {
        long court = 0, moyen = 0, long12 = 0, tresLong = 0;
        for (Object[] r : durRaw) {
            int  dur   = ((Number) r[0]).intValue();
            long count = ((Number) r[1]).longValue();
            if (dur <= 3)       court    += count;
            else if (dur <= 11) moyen    += count;
            else if (dur == 12) long12   += count;
            else                tresLong += count;
        }
        return List.of(court, moyen, long12, tresLong);
    }

    // ── Smart Insights ────────────────────────────────────────────────────────

    public List<BIInsightDTO> getInsights() {
        return securityUtils.isSuperAdmin()
                ? getInsightsGlobal()
                : getInsightsForAgency(securityUtils.getCurrentUserId());
    }

    private List<BIInsightDTO> getInsightsGlobal() {
        List<BIInsightDTO> insights = new ArrayList<>();
        LocalDateTime now            = LocalDateTime.now();
        LocalDateTime startOfMonth   = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startPrevMonth = startOfMonth.minusMonths(1);
        LocalDateTime sixMonthsAgo   = now.minusMonths(6);

        long stagnant = propertyRepository.countStagnantProperties(sixMonthsAgo);
        if (stagnant > 0) {
            insights.add(insight("warning", "fas fa-clock", "Biens stagnants",
                    stagnant + " bien(s) disponibles depuis plus de 6 mois sans transaction."));
        }

        double currRevenue = Optional.ofNullable(propertyRepository.calculateMonthlyRevenue(startOfMonth)).orElse(0.0);
        double prevRevenue = Optional.ofNullable(
                propertyRepository.calculateRevenueByMonth(startPrevMonth.getMonthValue(), startPrevMonth.getYear())
        ).orElse(0.0);
        addRevenueTrendInsight(insights, currRevenue, prevRevenue);

        long disponible = propertyRepository.countByStatut("DISPONIBLE");
        long vendu      = propertyRepository.countByStatut("VENDU");
        long loue       = propertyRepository.countByStatut("LOUE");
        addConversionInsight(insights, disponible, vendu, loue);

        List<Object[]> topCities = propertyRepository.getTopCitiesBySales(PageRequest.of(0, 1));
        if (!topCities.isEmpty()) {
            Object[] top = topCities.get(0);
            insights.add(insight("info", "fas fa-map-pin", "Zone la plus active",
                    String.format("%s est la ville avec le plus de ventes (%s biens vendus).", top[0], top[2])));
        }

        double unpaidTotal = Optional.ofNullable(affiliateTransactionRepository.getTotalUnpaidCommissions()).orElse(0.0);
        long   unpaidCount = affiliateTransactionRepository.countUnpaidTransactions();
        if (unpaidCount > 0) {
            insights.add(insight("warning", "fas fa-money-bill-wave", "Commissions en attente de paiement",
                    String.format("%d commission(s) non réglée(s) pour un total de %.0f TND à verser aux affiliés.", unpaidCount, unpaidTotal)));
        }

        if (vendu > 0 && loue > 0) {
            double rentRatio = loue * 100.0 / (vendu + loue);
            if (rentRatio > 60) {
                insights.add(insight("info", "fas fa-key", "Forte demande locative",
                        String.format("%.0f%% des transactions sont des locations. Envisagez d'élargir le portefeuille locatif.", rentRatio)));
            }
        }

        // Rental BI insight — expiring contracts
        long expiring = propertyRepository.countExpiringRentals(now, now.plusDays(30));
        if (expiring > 0) {
            insights.add(insight("warning", "fas fa-calendar-xmark", "Contrats locatifs expirant bientôt",
                    String.format("%d contrat(s) de location arrivent à échéance dans les 30 prochains jours.", expiring)));
        }

        double totalRentalRevenue = Optional.ofNullable(propertyRepository.getTotalActiveRentalRevenue()).orElse(0.0);
        if (totalRentalRevenue > 0) {
            insights.add(insight("info", "fas fa-coins", "Revenus locatifs actifs",
                    String.format("Revenus locatifs récurrents (MRR) actifs : %.0f TND/mois.", totalRentalRevenue)));
        }

        return insights;
    }

    private List<BIInsightDTO> getInsightsForAgency(Long adminId) {
        List<BIInsightDTO> insights = new ArrayList<>();
        LocalDateTime now            = LocalDateTime.now();
        LocalDateTime startOfMonth   = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startPrevMonth = startOfMonth.minusMonths(1);
        LocalDateTime sixMonthsAgo   = now.minusMonths(6);

        long stagnant = propertyRepository.countStagnantByAgencyAdmin(adminId, sixMonthsAgo);
        if (stagnant > 0) {
            insights.add(insight("warning", "fas fa-clock", "Biens stagnants",
                    stagnant + " bien(s) disponibles depuis plus de 6 mois sans transaction."));
        }

        double currRevenue = Optional.ofNullable(propertyRepository.calculateMonthlyRevenueByAgencyAdmin(adminId, startOfMonth)).orElse(0.0);
        double prevRevenue = Optional.ofNullable(
                propertyRepository.calculateRevenueByMonthByAgencyAdmin(adminId, startPrevMonth.getMonthValue(), startPrevMonth.getYear())
        ).orElse(0.0);
        addRevenueTrendInsight(insights, currRevenue, prevRevenue);

        long disponible = propertyRepository.countAvailableByAgencyAdmin(adminId);
        long vendu      = propertyRepository.countSoldByAgencyAdmin(adminId);
        long loue       = propertyRepository.countRentedByAgencyAdmin(adminId);
        addConversionInsight(insights, disponible, vendu, loue);

        List<Object[]> topCities = propertyRepository.getTopCitiesBySalesByAgencyAdmin(adminId, PageRequest.of(0, 1));
        if (!topCities.isEmpty()) {
            Object[] top = topCities.get(0);
            insights.add(insight("info", "fas fa-map-pin", "Zone la plus active",
                    String.format("%s est la ville avec le plus de ventes (%s biens vendus).", top[0], top[2])));
        }

        double unpaidTotal = Optional.ofNullable(affiliateTransactionRepository.getTotalUnpaidCommissionsByAgencyAdmin(adminId)).orElse(0.0);
        long   unpaidCount = affiliateTransactionRepository.countUnpaidTransactionsByAgencyAdmin(adminId);
        if (unpaidCount > 0) {
            insights.add(insight("warning", "fas fa-money-bill-wave", "Commissions en attente",
                    String.format("%d commission(s) non réglée(s) pour un total de %.0f TND.", unpaidCount, unpaidTotal)));
        }

        // Rental BI insight — expiring contracts for this agency
        long expiring = propertyRepository.countExpiringRentalsByAgency(adminId, now, now.plusDays(30));
        if (expiring > 0) {
            insights.add(insight("warning", "fas fa-calendar-xmark", "Contrats locatifs expirant bientôt",
                    String.format("%d contrat(s) arrivent à échéance dans les 30 prochains jours.", expiring)));
        }

        return insights;
    }

    // ── Shared insight helpers ────────────────────────────────────────────────

    private void addRevenueTrendInsight(List<BIInsightDTO> list, double currRevenue, double prevRevenue) {
        if (prevRevenue > 0) {
            double trendPct = (currRevenue - prevRevenue) / prevRevenue * 100;
            if (trendPct >= 10) {
                list.add(insight("success", "fas fa-arrow-trend-up", "Revenus en hausse",
                        String.format("Les revenus ont progressé de %.1f%% ce mois par rapport au mois précédent.", trendPct)));
            } else if (trendPct <= -10) {
                list.add(insight("danger", "fas fa-arrow-trend-down", "Baisse des revenus",
                        String.format("Les revenus ont reculé de %.1f%%. Une action commerciale est recommandée.", Math.abs(trendPct))));
            }
        } else if (currRevenue > 0) {
            list.add(insight("success", "fas fa-star", "Premières ventes du mois",
                    String.format("%.0f TND de chiffre d'affaires réalisé ce mois.", currRevenue)));
        }
    }

    private void addConversionInsight(List<BIInsightDTO> list, long disponible, long vendu, long loue) {
        if (disponible > 0 && vendu + loue > 0) {
            double convRate = (vendu + loue) * 100.0 / (disponible + vendu + loue);
            if (convRate < 20) {
                list.add(insight("info", "fas fa-lightbulb", "Taux de conversion à optimiser",
                        String.format("Seulement %.1f%% des biens ont été vendus/loués. Envisagez des actions marketing ciblées.", convRate)));
            } else if (convRate >= 50) {
                list.add(insight("success", "fas fa-trophy", "Excellent taux de conversion",
                        String.format("%.1f%% des biens ont trouvé preneur — performance au-dessus de la moyenne du marché.", convRate)));
            }
        }
    }

    private BIInsightDTO insight(String type, String icon, String title, String message) {
        return BIInsightDTO.builder().type(type).icon(icon).title(title).message(message).build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDateTime startOf12MonthsAgo() {
        return LocalDateTime.now()
                .minusMonths(11).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private List<String> buildMonthLabels(LocalDateTime since) {
        List<String> months = new ArrayList<>();
        LocalDateTime cursor = since;
        while (!cursor.isAfter(LocalDateTime.now())) {
            months.add(cursor.format(MONTH_LABEL_FMT));
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private List<LocalDateTime> buildMonthStarts(LocalDateTime since) {
        List<LocalDateTime> starts = new ArrayList<>();
        LocalDateTime cursor = since;
        while (!cursor.isAfter(LocalDateTime.now())) {
            starts.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return starts;
    }

    private Map<String, Long> toLongMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            int  month = ((Number) row[0]).intValue();
            int  year  = ((Number) row[1]).intValue();
            long val   = ((Number) row[2]).longValue();
            map.put(year + "-" + month, val);
        }
        return map;
    }

    private Map<String, Double> toDoubleMap(List<Object[]> rows) {
        Map<String, Double> map = new HashMap<>();
        for (Object[] row : rows) {
            int    month = ((Number) row[0]).intValue();
            int    year  = ((Number) row[1]).intValue();
            double val   = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            map.put(year + "-" + month, val);
        }
        return map;
    }

    private double trend(double current, double previous) {
        if (previous <= 0) return 0;
        return (current - previous) / previous * 100;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
