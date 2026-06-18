package com.immobilier.backend.service;

import com.immobilier.backend.dto.CommercialPerformanceDTO;
import com.immobilier.backend.dto.CommissionDetailDTO;
import com.immobilier.backend.dto.CommissionRowDTO;
import com.immobilier.backend.dto.CommissionSummaryDTO;
import com.immobilier.backend.dto.MyPerformanceDTO;
import com.immobilier.backend.entity.AffiliateTransaction;
import com.immobilier.backend.entity.Commission;
import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.PropertyShareRequest;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.enums.ShareRequestStatus;
import com.immobilier.backend.repository.AffiliateTransactionRepository;
import com.immobilier.backend.repository.CommissionRepository;
import com.immobilier.backend.repository.PropertyShareRequestRepository;
import com.immobilier.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Records and manages STAFF and AGENCY commissions.
 *
 * Recording is invoked from the terminal sale/rental completion points
 * (direct sale, validated cross-ownership sale, CRM lead conversion).
 * It is fully self-guarded and never throws — a commission-recording
 * failure must never roll back or block the underlying transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final CommissionRepository commissionRepository;
    private final CommissionCalculator calculator;
    private final PropertyShareRequestRepository shareRequestRepository;
    private final UserRepository userRepository;
    private final AffiliateTransactionRepository affiliateTransactionRepository;

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH);

    /**
     * Records staff + agency-direct commissions for a completed sale/rental.
     * @param transactionType "SALE" or "RENT"
     */
    @Transactional
    public void recordForCompletedSale(Property property, User broker, String transactionType) {
        recordForCompletedSale(property, broker, transactionType, null);
    }

    /**
     * @param staffRateOverride when non-null, this is the commission % the
     *        reviewing admin entered at validation time. It takes precedence
     *        over {@code User.commissionRate} so the STAFF commission is always
     *        recorded on approval (no 0-default skip, no 4% default).
     */
    @Transactional
    public void recordForCompletedSale(Property property, User broker, String transactionType,
                                       Double staffRateOverride) {
        try {
            if (property == null) return;
            double price = "SALE".equals(transactionType)
                    ? orZero(property.getPrixVente())
                    : orZero(property.getPrixLocation());
            if (price <= 0) return;

            recordStaffCommission(property, broker, transactionType, price, staffRateOverride);
            recordAgencyDirectCommission(property, broker, transactionType, price);
        } catch (Exception e) {
            log.warn("recordForCompletedSale failed for property {}: {}",
                    property != null ? property.getId() : null, e.getMessage());
        }
    }

    private void recordStaffCommission(Property property, User broker, String txType,
                                       double price, Double rateOverride) {
        if (broker == null) return;
        boolean isStaff = broker.getRole() == RoleType.COMMERCIAL
                       || broker.getRole() == RoleType.RESPONSABLE_COMMERCIAL;
        if (!isStaff) return;

        // Admin-entered rate wins; otherwise fall back to the per-user rate.
        Double rate = (rateOverride != null) ? rateOverride : broker.getCommissionRate();
        if (rate == null || rate <= 0) return;
        if (commissionRepository.existsByPropertyIdAndBeneficiaryTypeAndBeneficiaryId(
                property.getId(), "STAFF", broker.getId())) return;

        double amount = calculator.compute(price, rate, "PERCENTAGE");
        if (amount <= 0) return;

        Commission c = new Commission();
        c.setBeneficiary(broker);
        c.setBeneficiaryType("STAFF");
        c.setProperty(property);
        c.setTransactionType(txType);
        c.setPropertyPrice(price);
        c.setCommissionType("PERCENTAGE");
        c.setCommissionRate(rate);
        c.setCommissionAmount(amount);
        c.setStatus("PENDING");
        commissionRepository.save(c);
        log.info("STAFF commission {} TND recorded for user {} on property {}",
                amount, broker.getId(), property.getId());
    }

    private void recordAgencyDirectCommission(Property property, User broker, String txType, double price) {
        if (!"SUPER_ADMIN_OWNED".equals(property.getOwnerType())) return;

        User agencyAdmin = resolveSellingAgencyAdmin(broker);
        if (agencyAdmin == null) return;

        PropertyShareRequest sr = shareRequestRepository
                .findByPropertyIdAndAgencyAdminId(property.getId(), agencyAdmin.getId())
                .orElse(null);
        if (sr == null || sr.getStatus() != ShareRequestStatus.ACCEPTED) return;

        if (commissionRepository.existsByPropertyIdAndBeneficiaryTypeAndBeneficiaryId(
                property.getId(), "AGENCY", agencyAdmin.getId())) return;

        String ctype = sr.getCommissionType() != null ? sr.getCommissionType() : "PERCENTAGE";
        double rate  = sr.getCommissionPercentage() != null ? sr.getCommissionPercentage() : 0.0;
        double amount = calculator.compute(price, rate, ctype);
        if (amount <= 0) return;

        Commission c = new Commission();
        c.setBeneficiary(agencyAdmin);
        c.setBeneficiaryType("AGENCY");
        c.setProperty(property);
        c.setTransactionType(txType);
        c.setPropertyPrice(price);
        c.setCommissionType(ctype);
        c.setCommissionRate(rate);
        c.setCommissionAmount(amount);
        c.setStatus("PENDING");
        commissionRepository.save(c);
        log.info("AGENCY commission {} TND recorded for agency {} on shared property {}",
                amount, agencyAdmin.getId(), property.getId());
    }

    /** The agency that closed the deal: the broker if ADMIN, otherwise their top ADMIN ancestor. */
    private User resolveSellingAgencyAdmin(User broker) {
        if (broker == null) return null;
        if (broker.getRole() == RoleType.ADMIN) return broker;
        return userRepository.findTopAdminAncestor(broker.getId()).orElse(null);
    }

    // ── Management ────────────────────────────────────────────────────────────

    @Transactional
    public Commission markPaid(Long commissionId) {
        Commission c = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new RuntimeException("Commission introuvable: " + commissionId));
        c.setStatus("PAID");
        c.setPaidAt(LocalDateTime.now());
        return commissionRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<Commission> byBeneficiary(Long userId) {
        return commissionRepository.findByBeneficiaryIdOrderByCreatedAtDesc(userId);
    }

    // ── Dedicated AGENCY / STAFF commission pages (built on the same entity) ───

    /** A COMMERCIAL sees STRICTLY their own STAFF commissions — nothing else. */
    private boolean isStrictSelf(User u) {
        return u.getRole() == RoleType.COMMERCIAL;
    }

    /**
     * A RESPONSABLE_COMMERCIAL is a manager: they see their OWN STAFF
     * commissions PLUS those of their descendant COMMERCIALs (their team).
     * They never see other agencies or other responsables' teams.
     */
    private boolean isManager(User u) {
        return u.getRole() == RoleType.RESPONSABLE_COMMERCIAL;
    }

    /**
     * Detailed commission list for the dedicated pages — single source of truth,
     * scoped server-side. Never recomputed on the frontend.
     *
     * @param beneficiaryType "AGENCY" or "STAFF"
     *
     * Scopes:
     * <ul>
     *   <li>SUPER_ADMIN → every row of that type (all agencies)</li>
     *   <li>ADMIN → AGENCY: own agency commissions · STAFF: the staff of their agency</li>
     *   <li>COMMERCIAL / RESPONSABLE_COMMERCIAL → STAFF: only their own rows</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<CommissionDetailDTO> listDetailed(User caller, String beneficiaryType, String statusFilter) {
        boolean superAdmin = caller.getRole() == RoleType.SUPER_ADMIN;
        Long id = caller.getId();
        List<Commission> rows;

        if ("AGENCY".equals(beneficiaryType)) {
            rows = superAdmin
                    ? commissionRepository.findDetailedByType("AGENCY")
                    : commissionRepository.findDetailedByTypeAndBeneficiary("AGENCY", id);
        } else { // STAFF
            if (superAdmin) {
                rows = commissionRepository.findDetailedByType("STAFF");
            } else if (isStrictSelf(caller)) { // COMMERCIAL — strictly own
                rows = commissionRepository.findDetailedByTypeAndBeneficiary("STAFF", id);
            } else if (isManager(caller)) { // RESPONSABLE_COMMERCIAL — own + team
                rows = commissionRepository.findDetailedStaffForManager(id);
            } else { // ADMIN — staff of their agency hierarchy
                rows = commissionRepository.findDetailedStaffByAgencyAdmin(id);
            }
        }
        return rows.stream()
                .filter(c -> matchesStatus(statusFilter, "PAID".equals(c.getStatus())))
                .map(CommissionDetailDTO::from)
                .toList();
    }

    /** Totals for a commission scope — drives the page header + dashboard card. */
    @Transactional(readOnly = true)
    public CommissionSummaryDTO summary(User caller, String beneficiaryType) {
        boolean superAdmin = caller.getRole() == RoleType.SUPER_ADMIN;
        Long id = caller.getId();
        double paid, pending;
        long count;

        if (superAdmin) {
            paid    = orZero(commissionRepository.sumByTypeAndStatus(beneficiaryType, "PAID"));
            pending = orZero(commissionRepository.sumByTypeAndStatus(beneficiaryType, "PENDING"));
            count   = commissionRepository.countByTypeAndStatus(beneficiaryType, "PAID")
                    + commissionRepository.countByTypeAndStatus(beneficiaryType, "PENDING");
        } else if ("AGENCY".equals(beneficiaryType)) {
            paid    = orZero(commissionRepository.sumByTypeBeneficiaryAndStatus("AGENCY", id, "PAID"));
            pending = orZero(commissionRepository.sumByTypeBeneficiaryAndStatus("AGENCY", id, "PENDING"));
            count   = commissionRepository.countByTypeAndBeneficiary("AGENCY", id);
        } else if (isStrictSelf(caller)) { // COMMERCIAL — strictly own
            paid    = orZero(commissionRepository.sumByTypeBeneficiaryAndStatus("STAFF", id, "PAID"));
            pending = orZero(commissionRepository.sumByTypeBeneficiaryAndStatus("STAFF", id, "PENDING"));
            count   = commissionRepository.countByTypeAndBeneficiary("STAFF", id);
        } else if (isManager(caller)) { // RESPONSABLE_COMMERCIAL — own + team
            paid    = orZero(commissionRepository.sumStaffByManagerAndStatus(id, "PAID"));
            pending = orZero(commissionRepository.sumStaffByManagerAndStatus(id, "PENDING"));
            count   = commissionRepository.findDetailedStaffForManager(id).size();
        } else { // ADMIN — STAFF of their agency hierarchy
            paid    = orZero(commissionRepository.sumStaffByAgencyAdminAndStatus(id, "PAID"));
            pending = orZero(commissionRepository.sumStaffByAgencyAdminAndStatus(id, "PENDING"));
            count   = commissionRepository.findDetailedStaffByAgencyAdmin(id).size();
        }

        return CommissionSummaryDTO.builder()
                .total(round1(paid + pending))
                .paid(round1(paid))
                .pending(round1(pending))
                .count(count)
                .build();
    }

    /**
     * Mark paid, scoped to a beneficiary type so the dedicated endpoints can't
     * be used to pay an affiliate/other-type commission by id guessing.
     */
    @Transactional
    public CommissionDetailDTO markPaidScoped(Long commissionId, String beneficiaryType) {
        Commission c = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new RuntimeException("Commission introuvable: " + commissionId));
        if (!beneficiaryType.equals(c.getBeneficiaryType())) {
            throw new IllegalArgumentException(
                    "Cette commission n'est pas de type " + beneficiaryType + ".");
        }
        c.setStatus("PAID");
        c.setPaidAt(LocalDateTime.now());
        return CommissionDetailDTO.from(commissionRepository.save(c));
    }

    // ── Commercial performance (Gestion commerciale) ──────────────────────────

    /**
     * Per-commercial performance, aggregated from the SAME scoped STAFF
     * commission rows as {@link #listDetailed} — no parallel data source,
     * no fake data. One DB read (fetch-joined), grouped in memory.
     *
     * Scope: SUPER_ADMIN → all agencies · ADMIN → own agency staff ·
     * COMMERCIAL/RESPONSABLE_COMMERCIAL → only themselves.
     */
    @Transactional(readOnly = true)
    public List<CommercialPerformanceDTO> commercialPerformance(User caller) {
        boolean superAdmin = caller.getRole() == RoleType.SUPER_ADMIN;
        Long callerId = caller.getId();

        List<Commission> rows;
        if (superAdmin) {
            rows = commissionRepository.findDetailedByType("STAFF");
        } else if (isStrictSelf(caller)) { // COMMERCIAL — strictly own
            rows = commissionRepository.findDetailedByTypeAndBeneficiary("STAFF", callerId);
        } else if (isManager(caller)) { // RESPONSABLE_COMMERCIAL — own + team
            rows = commissionRepository.findDetailedStaffForManager(callerId);
        } else { // ADMIN — staff of their agency hierarchy
            rows = commissionRepository.findDetailedStaffByAgencyAdmin(callerId);
        }

        LocalDateTime monthStart = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        // Group by beneficiary, preserving first-seen order.
        Map<Long, CommercialPerformanceDTO.CommercialPerformanceDTOBuilder> acc = new LinkedHashMap<>();
        Map<Long, double[]> sums = new LinkedHashMap<>(); // [earned, paid, pending, ca, monthCommission]
        Map<Long, long[]>   counts = new LinkedHashMap<>(); // [sales, rentals]

        for (Commission c : rows) {
            User b = c.getBeneficiary();
            if (b == null) continue;
            Long bid = b.getId();

            acc.computeIfAbsent(bid, k -> {
                String agency = userRepository.findTopAdminAncestor(bid)
                        .map(User::getFullName).orElse("—");
                return CommercialPerformanceDTO.builder()
                        .commercialId(bid)
                        .name(b.getFullName())
                        .email(b.getEmail())
                        .role(b.getRole() != null ? b.getRole().name() : null)
                        .agencyName(agency)
                        .active(b.isActive())
                        .commissionRate(b.getCommissionRate() != null ? b.getCommissionRate() : 0.0);
            });
            double[] s = sums.computeIfAbsent(bid, k -> new double[5]);
            long[]   n = counts.computeIfAbsent(bid, k -> new long[2]);

            double amount = c.getCommissionAmount() != null ? c.getCommissionAmount() : 0.0;
            double price  = c.getPropertyPrice() != null ? c.getPropertyPrice() : 0.0;
            boolean paid  = "PAID".equals(c.getStatus());

            s[0] += amount;                       // earned
            if (paid) s[1] += amount; else s[2] += amount; // paid / pending
            s[3] += price;                        // CA
            if (c.getCreatedAt() != null && !c.getCreatedAt().isBefore(monthStart)) {
                s[4] += amount;                   // this-month commission
            }
            if ("RENT".equals(c.getTransactionType())) n[1]++; else n[0]++;
        }

        List<CommercialPerformanceDTO> out = new ArrayList<>();
        for (Map.Entry<Long, CommercialPerformanceDTO.CommercialPerformanceDTOBuilder> e : acc.entrySet()) {
            double[] s = sums.get(e.getKey());
            long[]   n = counts.get(e.getKey());
            out.add(e.getValue()
                    .salesCount(n[0])
                    .rentalsCount(n[1])
                    .dealsClosed(n[0] + n[1])
                    .revenueGenerated(round1(s[3]))
                    .revenueThisMonth(round1(s[4]))
                    .commissionsEarned(round1(s[0]))
                    .commissionsPaid(round1(s[1]))
                    .commissionsPending(round1(s[2]))
                    .build());
        }
        out.sort((a, b) -> Double.compare(b.getCommissionsEarned(), a.getCommissionsEarned()));
        return out;
    }

    // ── Personal performance (COMMERCIAL / RESPONSABLE_COMMERCIAL) ────────────

    @Transactional(readOnly = true)
    public MyPerformanceDTO getMyPerformance(User user) {
        Long uid = user.getId();
        double total   = orZero(commissionRepository.sumByBeneficiaryAndStatus(uid, "PENDING"))
                       + orZero(commissionRepository.sumByBeneficiaryAndStatus(uid, "PAID"));
        double pending = orZero(commissionRepository.sumByBeneficiaryAndStatus(uid, "PENDING"));
        double paid    = orZero(commissionRepository.sumByBeneficiaryAndStatus(uid, "PAID"));
        long   sales   = commissionRepository.countByBeneficiaryAndType(uid, "SALE");
        long   rentals = commissionRepository.countByBeneficiaryAndType(uid, "RENT");

        // 12-month commission trend from this user's commission rows
        List<String> months = new ArrayList<>();
        List<Double> series = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.now()
                .minusMonths(11).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<Commission> rows = commissionRepository.findByBeneficiaryIdOrderByCreatedAtDesc(uid);
        while (!cursor.isAfter(LocalDateTime.now())) {
            final LocalDateTime mStart = cursor;
            final LocalDateTime mEnd = cursor.plusMonths(1);
            double sum = rows.stream()
                    .filter(c -> c.getCreatedAt() != null
                            && !c.getCreatedAt().isBefore(mStart)
                            && c.getCreatedAt().isBefore(mEnd))
                    .mapToDouble(c -> c.getCommissionAmount() != null ? c.getCommissionAmount() : 0.0)
                    .sum();
            months.add(cursor.format(MONTH_FMT));
            series.add(Math.round(sum * 10.0) / 10.0);
            cursor = cursor.plusMonths(1);
        }

        return MyPerformanceDTO.builder()
                .salesCount(sales)
                .rentalsCount(rentals)
                .totalCommission(round1(total))
                .pendingCommission(round1(pending))
                .paidCommission(round1(paid))
                .commissionRate(user.getCommissionRate() != null ? user.getCommissionRate() : 0.0)
                .months(months)
                .monthlyCommissions(series)
                .build();
    }

    // ── Unified commission history (affiliate + agency + staff) ──────────────

    @Transactional(readOnly = true)
    public List<CommissionRowDTO> listScoped(User caller, String statusFilter) {
        List<CommissionRowDTO> rows = new ArrayList<>();
        boolean superAdmin = caller.getRole() == RoleType.SUPER_ADMIN;

        // Commission rows (AGENCY + STAFF)
        List<Commission> comm = superAdmin
                ? commissionRepository.findAll()
                : commissionRepository.findScopedForAgency(caller.getId());
        for (Commission c : comm) {
            if (!matchesStatus(statusFilter, "PAID".equals(c.getStatus()))) continue;
            User b = c.getBeneficiary();
            rows.add(CommissionRowDTO.builder()
                    .id(c.getId())
                    .source(c.getBeneficiaryType())
                    .beneficiaryName(b != null ? safeName(b) : "—")
                    .beneficiaryEmail(b != null ? b.getEmail() : "")
                    .propertyId(c.getProperty() != null ? c.getProperty().getId() : null)
                    .propertyTitle(c.getProperty() != null ? c.getProperty().getTitre() : "—")
                    .transactionType(c.getTransactionType())
                    .propertyPrice(c.getPropertyPrice() != null ? c.getPropertyPrice() : 0.0)
                    .commissionAmount(c.getCommissionAmount() != null ? c.getCommissionAmount() : 0.0)
                    .paid("PAID".equals(c.getStatus()))
                    .date(c.getCreatedAt())
                    .build());
        }

        // Affiliate rows
        List<AffiliateTransaction> aff = superAdmin
                ? affiliateTransactionRepository.findAll()
                : affiliateTransactionRepository.findByAgencyAdminIdOrderByDateDesc(caller.getId());
        for (AffiliateTransaction t : aff) {
            boolean paid = Boolean.TRUE.equals(t.getIsPaid());
            if (!matchesStatus(statusFilter, paid)) continue;
            User b = t.getAffiliate();
            rows.add(CommissionRowDTO.builder()
                    .id(t.getId())
                    .source("AFFILIATE")
                    .beneficiaryName(b != null ? safeName(b) : "—")
                    .beneficiaryEmail(b != null ? b.getEmail() : "")
                    .propertyId(t.getProperty() != null ? t.getProperty().getId() : null)
                    .propertyTitle(t.getProperty() != null ? t.getProperty().getTitre() : "—")
                    .transactionType(t.getTransactionType())
                    .propertyPrice(t.getPropertyPrice() != null ? t.getPropertyPrice() : 0.0)
                    .commissionAmount(t.getCommissionAmount() != null ? t.getCommissionAmount() : 0.0)
                    .paid(paid)
                    .date(t.getTransactionDate())
                    .build());
        }

        rows.sort((a, b) -> {
            LocalDateTime da = a.getDate(), db = b.getDate();
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });
        return rows;
    }

    private boolean matchesStatus(String filter, boolean paid) {
        if (filter == null || filter.isBlank()) return true;
        if ("PAID".equalsIgnoreCase(filter)) return paid;
        if ("PENDING".equalsIgnoreCase(filter)) return !paid;
        return true;
    }

    private String safeName(User u) {
        String p = u.getPrenom() != null ? u.getPrenom() : "";
        String n = u.getNom() != null ? u.getNom() : "";
        return (p + " " + n).trim();
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private double orZero(Double v) { return v != null ? v : 0.0; }
}
