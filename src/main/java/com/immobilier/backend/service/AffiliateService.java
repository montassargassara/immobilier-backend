package com.immobilier.backend.service;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.entity.*;
import com.immobilier.backend.enums.AffiliateStatus;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.enums.SaleOfferStatus;
import com.immobilier.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AffiliateService {

    private final UserRepository userRepository;
    private final AffiliateProfileRepository affiliateProfileRepository;
    private final AffiliateRegionRepository affiliateRegionRepository;
    private final AffiliateTransactionRepository affiliateTransactionRepository;
    private final AffiliateActivityRepository affiliateActivityRepository;
    private final PropertyRepository propertyRepository;
    private final SaleOfferRepository saleOfferRepository;
    private final ClientInfoRepository clientInfoRepository;
    private final AffiliateCustomerRelationRepository affiliateCustomerRelationRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Public registration: creates User (isActive=false) + AffiliateProfile (PENDING).
     * Super Admin must approve before the affiliate can log in.
     */
    @Transactional
    public AffiliateProfileDTO registerAffiliate(CreateAffiliateRequest request) {
        log.info("New affiliate registration: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        String tel = request.getTelephone();
        user.setTelephone((tel != null && !tel.isBlank()) ? tel : null);
        user.setRole(RoleType.AFFILIATE);
        user.setIsActive(false); // not active until approved

        User savedUser = userRepository.save(user);

        AffiliateProfile profile = new AffiliateProfile();
        profile.setUser(savedUser);
        profile.setStatus(AffiliateStatus.PENDING);
        profile.setExperienceLevel(request.getExperienceLevel());
        profile.setNotes(request.getNotes());
        affiliateProfileRepository.save(profile);

        if (request.getSelectedRegions() != null) {
            for (RegionSelection rs : request.getSelectedRegions()) {
                AffiliateRegion region = new AffiliateRegion();
                region.setAffiliate(savedUser);
                region.setRegionName(rs.getRegionName());
                region.setCountry(rs.getCountry());
                region.setCity(rs.getCity());
                region.setRegionDescription(rs.getRegionDescription());
                region.setIsActive(true);
                affiliateRegionRepository.save(region);
            }
        }

        // Notify all Super Admins
        userRepository.findByRole(RoleType.SUPER_ADMIN).forEach(superAdmin ->
            notificationService.create(
                superAdmin,
                NotificationType.AFFILIATE_REGISTRATION,
                "Nouvelle demande d'affiliation",
                "Un nouvel affilié a soumis une demande : " + savedUser.getFullName() + " (" + savedUser.getEmail() + ")",
                savedUser.getId()
            )
        );

        log.info("Affiliate created with status PENDING, id={}", savedUser.getId());
        return toProfileDTO(profile);
    }

    // ── Approval workflow (Super Admin only) ─────────────────────────────────

    @Transactional
    public AffiliateProfileDTO approveAffiliate(Long affiliateId, Long approverId) {
        AffiliateProfile profile = getProfileOrThrow(affiliateId);
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        if (profile.getStatus() != AffiliateStatus.PENDING) {
            throw new RuntimeException("Seuls les affiliés PENDING peuvent être approuvés");
        }

        profile.setStatus(AffiliateStatus.ACTIVE);
        profile.setReviewedBy(approver);
        profile.setReviewedAt(LocalDateTime.now());

        User affiliateUser = profile.getUser();
        affiliateUser.setIsActive(true);
        userRepository.save(affiliateUser);
        affiliateProfileRepository.save(profile);

        // Auto-create ClientInfo so the affiliate appears in Gestion des clients
        if (!clientInfoRepository.findByUserId(affiliateUser.getId()).isPresent()) {
            String zone = affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(affiliateUser.getId())
                .stream().findFirst()
                .map(r -> r.getCountry() != null && r.getCity() != null
                    ? r.getCountry() + ", " + r.getCity()
                    : r.getRegionName())
                .orElse(null);

            ClientInfo clientInfo = new ClientInfo();
            clientInfo.setUser(affiliateUser);
            clientInfo.setCreatedBy(approver);
            clientInfo.setVisibilityType("AGENCY_CLIENT");
            clientInfo.setAgencyAdminId(null);
            clientInfo.setZoneRecherchee(zone);
            clientInfo.setCodeAffiliation("AFF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            clientInfo.setTauxCommission(5.0);
            clientInfoRepository.save(clientInfo);
            log.info("ClientInfo created for self-registered affiliate userId={}", affiliateUser.getId());
        }

        notificationService.create(
            affiliateUser,
            NotificationType.AFFILIATE_APPROVED,
            "Compte affilié approuvé",
            "Félicitations ! Votre compte affilié a été approuvé. Vous pouvez maintenant accéder à la plateforme.",
            affiliateUser.getId()
        );

        log.info("Affiliate {} approved by {}", affiliateId, approverId);
        return toProfileDTO(profile);
    }

    @Transactional
    public AffiliateProfileDTO rejectAffiliate(Long affiliateId, String reason, Long reviewerId) {
        AffiliateProfile profile = getProfileOrThrow(affiliateId);
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new RuntimeException("Reviewer not found"));

        if (profile.getStatus() != AffiliateStatus.PENDING) {
            throw new RuntimeException("Seuls les affiliés PENDING peuvent être rejetés");
        }

        profile.setStatus(AffiliateStatus.REJECTED);
        profile.setReviewedBy(reviewer);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setRejectionReason(reason);
        affiliateProfileRepository.save(profile);

        notificationService.create(
            profile.getUser(),
            NotificationType.AFFILIATE_REJECTED,
            "Demande d'affiliation rejetée",
            "Votre demande d'affiliation a été rejetée" + (reason != null ? " : " + reason : "."),
            profile.getUser().getId()
        );

        log.info("Affiliate {} rejected by {}", affiliateId, reviewerId);
        return toProfileDTO(profile);
    }

    @Transactional
    public AffiliateProfileDTO suspendAffiliate(Long affiliateId, String reason, Long reviewerId) {
        AffiliateProfile profile = getProfileOrThrow(affiliateId);
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new RuntimeException("Reviewer not found"));

        profile.setStatus(AffiliateStatus.SUSPENDED);
        profile.setReviewedBy(reviewer);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setRejectionReason(reason);
        profile.getUser().setIsActive(false);
        userRepository.save(profile.getUser());
        affiliateProfileRepository.save(profile);

        notificationService.create(
            profile.getUser(),
            NotificationType.AFFILIATE_SUSPENDED,
            "Compte affilié suspendu",
            "Votre compte affilié a été suspendu" + (reason != null ? " : " + reason : "."),
            profile.getUser().getId()
        );

        log.info("Affiliate {} suspended by {}", affiliateId, reviewerId);
        return toProfileDTO(profile);
    }

    @Transactional
    public AffiliateProfileDTO activateAffiliate(Long affiliateId, Long reviewerId) {
        AffiliateProfile profile = getProfileOrThrow(affiliateId);
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new RuntimeException("Reviewer not found"));

        profile.setStatus(AffiliateStatus.ACTIVE);
        profile.setReviewedBy(reviewer);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setRejectionReason(null);
        profile.getUser().setIsActive(true);
        userRepository.save(profile.getUser());
        affiliateProfileRepository.save(profile);

        log.info("Affiliate {} re-activated by {}", affiliateId, reviewerId);
        return toProfileDTO(profile);
    }

    // ── Profile queries ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AffiliateProfileDTO getMyProfile(Long affiliateId) {
        return affiliateProfileRepository.findByUserId(affiliateId)
                .map(this::toProfileDTO)
                .orElseGet(() -> {
                    // Profile not yet created — build a minimal DTO from the user record
                    User user = userRepository.findById(affiliateId)
                            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
                    AffiliateProfileDTO dto = new AffiliateProfileDTO();
                    dto.setUserId(affiliateId);
                    dto.setEmail(user.getEmail());
                    dto.setNom(user.getNom());
                    dto.setPrenom(user.getPrenom());
                    dto.setTelephone(user.getTelephone());
                    dto.setIsActive(user.getIsActive());
                    dto.setStatus(AffiliateStatus.PENDING);
                    dto.setRegions(new java.util.ArrayList<>());
                    dto.setCreatedAt(LocalDateTime.now());
                    return dto;
                });
    }

    @Transactional(readOnly = true)
    public List<AffiliateProfileDTO> getAllAffiliates() {
        return affiliateProfileRepository.findAllOrderByCreatedAtDesc()
                .stream().map(this::toProfileDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AffiliateProfileDTO> getPendingAffiliates() {
        return affiliateProfileRepository.findByStatusOrderByCreatedAtDesc(AffiliateStatus.PENDING)
                .stream().map(this::toProfileDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AffiliateProfileDTO getAffiliateById(Long affiliateId) {
        AffiliateProfile profile = getProfileOrThrow(affiliateId);
        return toProfileDTO(profile);
    }

    // ── Zone-aware property listing ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AffiliatePropertyDTO> getEligiblePropertiesForAffiliate(Long affiliateId) {
        // Return empty list when profile missing or not yet ACTIVE (avoids 500 for pending users)
        boolean isActive = affiliateProfileRepository.findByUserId(affiliateId)
                .map(p -> p.getStatus() == AffiliateStatus.ACTIVE)
                .orElse(false);
        if (!isActive) return new ArrayList<>();

        List<AffiliateRegion> regions = affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(affiliateId);
        if (regions.isEmpty()) return new ArrayList<>();

        // Strict zone keys for affiliates with explicit country + city
        List<String> strictZoneKeys = regions.stream()
                .filter(r -> r.getCountry() != null && !r.getCountry().isBlank()
                          && r.getCity() != null && !r.getCity().isBlank())
                .map(r -> (r.getCountry().trim().toLowerCase()
                        + "|" + r.getCity().trim().toLowerCase()))
                .distinct()
                .collect(Collectors.toList());

        // Legacy fallback for regions that only have a regionName
        List<String> legacyNames = regions.stream()
                .filter(r -> r.getCountry() == null || r.getCountry().isBlank()
                          || r.getCity() == null || r.getCity().isBlank())
                .map(r -> r.getRegionName() != null ? r.getRegionName().trim().toLowerCase() : null)
                .filter(s -> s != null && !s.isBlank() && !s.contains(","))
                .distinct()
                .collect(Collectors.toList());

        java.util.LinkedHashMap<Long, Property> merged = new java.util.LinkedHashMap<>();
        if (!strictZoneKeys.isEmpty()) {
            propertyRepository.findEligiblePropertiesForAffiliateZoneKeys(strictZoneKeys)
                    .forEach(p -> merged.put(p.getId(), p));
        }
        if (!legacyNames.isEmpty()) {
            propertyRepository.findEligiblePropertiesForAffiliateRegions(legacyNames)
                    .forEach(p -> merged.put(p.getId(), p));
        }

        return merged.values().stream()
                .map(this::toAffiliatePropertyDTO)
                .collect(Collectors.toList());
    }

    // ── Region management ─────────────────────────────────────────────────────

    private static final int MAX_ZONES           = 3;
    private static final double STANDARD_ZONE_PRICE = 50.0;
    private static final double PREMIUM_ZONE_PRICE  = 100.0;
    private static final int    PREMIUM_DEMAND_THRESHOLD = 3;

    /**
     * Adds a new zone to the affiliate's account.
     * - First zone is always free.
     * - Zones 2 and 3 require a simulated payment (50 TND standard, 100 TND premium).
     * - Max 3 active zones enforced.
     */
    @Transactional
    public AffiliateRegionDTO addZone(Long affiliateId, AddZoneRequest req) {
        assertAffiliateActive(affiliateId);

        User affiliate = userRepository.findById(affiliateId)
                .orElseThrow(() -> new RuntimeException("Affiliate not found"));

        List<AffiliateRegion> existing = affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(affiliateId);
        if (existing.size() >= MAX_ZONES) {
            throw new RuntimeException("Vous avez atteint le maximum de " + MAX_ZONES + " zones actives.");
        }

        // Duplicate check (country + city)
        String newKey = buildZoneKey(req.getCountry(), req.getCity());
        if (newKey != null) {
            boolean duplicate = existing.stream().anyMatch(r -> newKey.equals(buildZoneKey(r.getCountry(), r.getCity())));
            if (duplicate) throw new RuntimeException("Cette zone est déjà dans votre compte.");
        }

        boolean isFreeZone = existing.isEmpty();
        boolean premiumZone = computeIsPremiumZone(req.getCountry(), req.getCity());
        double price = isFreeZone ? 0.0 : (premiumZone ? PREMIUM_ZONE_PRICE : STANDARD_ZONE_PRICE);

        if (!isFreeZone && (req.getPaymentConfirmed() == null || !req.getPaymentConfirmed())) {
            throw new RuntimeException("Le paiement de " + (int) price + " TND doit être confirmé pour ajouter cette zone.");
        }

        String regionName = (req.getCity() != null && !req.getCity().isBlank())
                ? req.getCity() : (req.getCountry() != null ? req.getCountry() : "Zone");

        AffiliateRegion region = new AffiliateRegion();
        region.setAffiliate(affiliate);
        region.setRegionName(regionName.trim().toLowerCase());
        region.setCountry(req.getCountry());
        region.setCity(req.getCity());
        region.setRegionDescription(req.getCountry());
        region.setIsActive(true);
        region.setIsPaid(!isFreeZone);
        region.setPricePaid(isFreeZone ? null : price);
        region.setIsPremium(premiumZone);

        return toRegionDTO(affiliateRegionRepository.save(region));
    }

    /** Returns true when the given zone is classified as premium (high demand). */
    private boolean computeIsPremiumZone(String country, String city) {
        String key = buildZoneKey(country, city);
        if (key == null) return false;
        long demand = saleOfferRepository.findAcceptedOrCompletedOffers().stream()
                .filter(o -> o.getProperty() != null)
                .filter(o -> key.equals(buildZoneKey(o.getProperty().getCountry(), o.getProperty().getCity())))
                .count();
        return demand >= PREMIUM_DEMAND_THRESHOLD;
    }

    @Transactional(readOnly = true)
    public List<AffiliateRegionDTO> getRegions(Long affiliateId) {
        return affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(affiliateId)
                .stream().map(this::toRegionDTO).collect(Collectors.toList());
    }

    @Transactional
    public AffiliateRegionDTO updateRegion(Long affiliateId, Long regionId, RegionSelection rs) {
        AffiliateRegion region = affiliateRegionRepository.findById(regionId)
                .orElseThrow(() -> new RuntimeException("Region not found"));

        if (!region.getAffiliate().getId().equals(affiliateId)) {
            throw new RuntimeException("Cette zone n'appartient pas à cet affilié");
        }

        if (rs.getRegionName()        != null) region.setRegionName(rs.getRegionName());
        if (rs.getCountry()           != null) region.setCountry(rs.getCountry());
        if (rs.getCity()              != null) region.setCity(rs.getCity());
        if (rs.getRegionDescription() != null) region.setRegionDescription(rs.getRegionDescription());

        return toRegionDTO(affiliateRegionRepository.save(region));
    }

    @Transactional
    public void removeZone(Long affiliateId, Long regionId) {
        AffiliateRegion region = affiliateRegionRepository.findById(regionId)
                .orElseThrow(() -> new RuntimeException("Region not found"));

        if (!region.getAffiliate().getId().equals(affiliateId)) {
            throw new RuntimeException("Cette zone n'appartient pas à cet affilié");
        }
        affiliateRegionRepository.delete(region);
    }

    /** Legacy alias — kept so existing callers (SuperAdminAffiliateController etc.) don't break. */
    @Transactional
    public void removeRegion(Long affiliateId, Long regionId) {
        removeZone(affiliateId, regionId);
    }

    /** Legacy alias used by admin-side region creation paths. */
    @Transactional
    public AffiliateRegionDTO addRegion(Long affiliateId, RegionSelection rs) {
        User affiliate = userRepository.findById(affiliateId)
                .orElseThrow(() -> new RuntimeException("Affiliate not found"));

        if (affiliateRegionRepository.findByAffiliateIdAndRegionName(affiliateId, rs.getRegionName()).isPresent()) {
            throw new RuntimeException("Cette zone est déjà assignée à cet affilié");
        }

        AffiliateRegion region = new AffiliateRegion();
        region.setAffiliate(affiliate);
        region.setRegionName(rs.getRegionName());
        region.setCountry(rs.getCountry());
        region.setCity(rs.getCity());
        region.setRegionDescription(rs.getRegionDescription());
        region.setIsActive(true);
        region.setIsPaid(false);
        region.setIsPremium(false);

        return toRegionDTO(affiliateRegionRepository.save(region));
    }

    // ── Activity tracking ─────────────────────────────────────────────────────

    @Transactional
    public void trackActivity(Long affiliateId, String activityType, Long propertyId, String metadata) {
        User affiliate = userRepository.findById(affiliateId)
                .orElseThrow(() -> new RuntimeException("Affiliate not found"));

        AffiliateActivity activity = new AffiliateActivity();
        activity.setAffiliate(affiliate);
        activity.setActivityType(activityType);
        activity.setPropertyId(propertyId);
        activity.setActivityDate(LocalDateTime.now());
        activity.setMetadata(metadata);
        affiliateActivityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public List<AffiliateActivityDTO> getActivities(Long affiliateId) {
        User affiliate = userRepository.findById(affiliateId)
                .orElseThrow(() -> new RuntimeException("Affiliate not found"));
        return affiliateActivityRepository.findByAffiliateOrderByActivityDateDesc(affiliate)
                .stream().map(this::toActivityDTO).collect(Collectors.toList());
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AffiliateStatsDTO getStats(Long affiliateId) {
        User affiliate = userRepository.findById(affiliateId)
                .orElseThrow(() -> new RuntimeException("Affiliate not found"));

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        AffiliateStatsDTO stats = new AffiliateStatsDTO();
        stats.setAffiliateId(affiliateId);
        stats.setAffiliateName(affiliate.getFullName());

        stats.setTotalViews(affiliateActivityRepository.countActivitiesByType(affiliate, "VIEW", monthStart).intValue());
        stats.setTotalShares(affiliateActivityRepository.countActivitiesByType(affiliate, "SHARE", monthStart).intValue());
        stats.setTotalContacts(affiliateActivityRepository.countActivitiesByType(affiliate, "CONTACT", monthStart).intValue());
        stats.setTotalVisits(affiliateActivityRepository.countActivitiesByType(affiliate, "VISIT", monthStart).intValue());

        List<AffiliateTransaction> allTx = affiliateTransactionRepository.findByAffiliateOrderByTransactionDateDesc(affiliate);
        stats.setTotalSales(allTx.size());
        stats.setTotalRevenue(allTx.stream().mapToDouble(AffiliateTransaction::getCommissionAmount).sum());

        Double pending = affiliateTransactionRepository.getTotalPendingCommission(affiliate);
        Double paid    = affiliateTransactionRepository.getTotalPaidCommission(affiliate);
        stats.setPendingCommission(pending != null ? pending : 0.0);
        stats.setPaidCommission(paid != null ? paid : 0.0);

        stats.setTotalOffersPending((int) saleOfferRepository.countByAffiliateIdAndStatus(affiliateId, SaleOfferStatus.PENDING));
        stats.setTotalOffersAccepted((int) saleOfferRepository.countByAffiliateIdAndStatus(affiliateId, SaleOfferStatus.ACCEPTED));
        stats.setTotalOffersRejected((int) saleOfferRepository.countByAffiliateIdAndStatus(affiliateId, SaleOfferStatus.REJECTED));

        stats.setConversionRate(stats.getTotalViews() > 0
            ? (double) stats.getTotalSales() / stats.getTotalViews() * 100 : 0.0);

        affiliateActivityRepository.findByAffiliateOrderByActivityDateDesc(affiliate)
            .stream().findFirst().ifPresent(a -> stats.setLastActivity(a.getActivityDate()));

        return stats;
    }

    // ── Ranking ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AffiliateStatsDTO> getMonthlyRanking() {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        List<Object[]> raw = affiliateTransactionRepository.getRankingByAllCommissions(monthStart);
        List<AffiliateStatsDTO> ranking = new ArrayList<>();

        int rank = 1;
        for (Object[] row : raw) {
            User affiliate = (User) row[0];
            Long saleCount  = (Long) row[1];
            Double revenue  = (Double) row[2];

            AffiliateStatsDTO s = new AffiliateStatsDTO();
            s.setAffiliateId(affiliate.getId());
            s.setAffiliateName(affiliate.getFullName());
            s.setTotalSales(saleCount.intValue());
            s.setTotalRevenue(revenue);
            s.setRank(rank++);

            ranking.add(s);
        }
        return ranking;
    }

    @Transactional(readOnly = true)
    public AffiliateStatsDTO getMyRankingPosition(Long affiliateId) {
        List<AffiliateStatsDTO> ranking = getMonthlyRanking();
        for (AffiliateStatsDTO s : ranking) {
            if (s.getAffiliateId().equals(affiliateId)) {
                return s;
            }
        }
        // Not ranked yet
        AffiliateStatsDTO stats = getStats(affiliateId);
        stats.setRank(0);
        return stats;
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AffiliateTransactionDTO> getTransactions(Long affiliateId) {
        User affiliate = userRepository.findById(affiliateId)
                .orElseThrow(() -> new RuntimeException("Affiliate not found"));
        Map<Long, String> buyerByTx = buyerNameByTransaction(
                affiliateCustomerRelationRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId));
        return affiliateTransactionRepository.findByAffiliateOrderByTransactionDateDesc(affiliate)
                .stream()
                .map(t -> enrichWithBuyer(toTransactionDTO(t), buyerByTx.get(t.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AffiliateTransactionDTO> getAllTransactions() {
        Map<Long, String> buyerByTx = buyerNameByTransaction(
                affiliateCustomerRelationRepository.findAllByOrderByCreatedAtDesc());
        return affiliateTransactionRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()))
                .map(t -> enrichWithBuyer(toTransactionDTO(t), buyerByTx.get(t.getId())))
                .collect(Collectors.toList());
    }

    // ── Affiliate-brought customers (CRM leads — never User accounts) ─────────

    @Transactional(readOnly = true)
    public List<AffiliateCustomerDTO> getAllAffiliateCustomers() {
        return affiliateCustomerRelationRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toCustomerDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AffiliateCustomerDTO> getCustomersForAffiliate(Long affiliateId) {
        return affiliateCustomerRelationRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId)
                .stream().map(this::toCustomerDTO).collect(Collectors.toList());
    }

    private Map<Long, String> buyerNameByTransaction(List<AffiliateCustomerRelation> relations) {
        return relations.stream()
                .filter(r -> r.getAffiliateTransaction() != null && r.getBuyerName() != null)
                .collect(Collectors.toMap(
                        r -> r.getAffiliateTransaction().getId(),
                        AffiliateCustomerRelation::getBuyerName,
                        (a, b) -> a));
    }

    private AffiliateTransactionDTO enrichWithBuyer(AffiliateTransactionDTO dto, String buyerName) {
        dto.setBuyerName(buyerName);
        dto.setViaAffiliate(buyerName != null);
        return dto;
    }

    private AffiliateCustomerDTO toCustomerDTO(AffiliateCustomerRelation r) {
        AffiliateTransaction tx = r.getAffiliateTransaction();
        return AffiliateCustomerDTO.builder()
                .id(r.getId())
                .affiliateId(r.getAffiliate().getId())
                .affiliateName(r.getAffiliate().getFullName())
                .affiliateEmail(r.getAffiliate().getEmail())
                .buyerName(r.getBuyerName())
                .buyerEmail(r.getBuyerEmail())
                .buyerPhone(r.getBuyerPhone())
                .propertyId(r.getProperty() != null ? r.getProperty().getId() : null)
                .propertyTitle(r.getProperty() != null ? r.getProperty().getTitre() : null)
                .transactionType(r.getTransactionType())
                .propertyPrice(r.getPropertyPrice())
                .commissionAmount(r.getCommissionAmount())
                .commissionPaid(tx != null && Boolean.TRUE.equals(tx.getIsPaid()))
                .offerStatus(r.getSaleOffer() != null && r.getSaleOffer().getStatus() != null
                        ? r.getSaleOffer().getStatus().name()
                        : "COMPLETED")
                .createdAt(r.getCreatedAt())
                .build();
    }

    /**
     * Marks the commission paid and RETURNS the updated, buyer-enriched DTO.
     * Returning the mutated resource (proper REST semantics) is what lets the
     * frontend update its row immutably without a reload — and prevents the
     * null-body crash that an empty 200 response caused.
     */
    @Transactional
    public AffiliateTransactionDTO markCommissionPaid(Long transactionId) {
        AffiliateTransaction tx = affiliateTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        tx.setIsPaid(true);
        tx.setPaymentDate(LocalDateTime.now());
        AffiliateTransaction saved = affiliateTransactionRepository.save(tx);

        String buyerName = affiliateCustomerRelationRepository
                .findByAffiliateTransactionId(saved.getId())
                .map(AffiliateCustomerRelation::getBuyerName)
                .orElse(null);
        return enrichWithBuyer(toTransactionDTO(saved), buyerName);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private AffiliateProfile getProfileOrThrow(Long affiliateId) {
        return affiliateProfileRepository.findByUserId(affiliateId)
                .orElseThrow(() -> new RuntimeException("Profil affilié introuvable pour userId=" + affiliateId));
    }

    private void assertAffiliateActive(Long affiliateId) {
        AffiliateProfile profile = getProfileOrThrow(affiliateId);
        if (profile.getStatus() != AffiliateStatus.ACTIVE) {
            throw new RuntimeException("Votre compte affilié n'est pas encore actif");
        }
    }

    // ── DTO converters ────────────────────────────────────────────────────────

    public AffiliateProfileDTO toProfileDTO(AffiliateProfile p) {
        AffiliateProfileDTO dto = new AffiliateProfileDTO();
        dto.setId(p.getId());
        dto.setUserId(p.getUser().getId());
        dto.setEmail(p.getUser().getEmail());
        dto.setNom(p.getUser().getNom());
        dto.setPrenom(p.getUser().getPrenom());
        dto.setTelephone(p.getUser().getTelephone());
        dto.setIsActive(p.getUser().getIsActive());
        dto.setStatus(p.getStatus());
        dto.setExperienceLevel(p.getExperienceLevel());
        dto.setNotes(p.getNotes());
        dto.setRejectionReason(p.getRejectionReason());
        dto.setReviewedAt(p.getReviewedAt());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());

        if (p.getReviewedBy() != null) {
            dto.setReviewedById(p.getReviewedBy().getId());
            dto.setReviewedByName(p.getReviewedBy().getFullName());
        }

        dto.setRegions(affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(p.getUser().getId())
                .stream().map(this::toRegionDTO).collect(Collectors.toList()));
        return dto;
    }

    private AffiliateRegionDTO toRegionDTO(AffiliateRegion r) {
        AffiliateRegionDTO dto = new AffiliateRegionDTO();
        dto.setId(r.getId());
        dto.setAffiliateId(r.getAffiliate().getId());
        dto.setAffiliateName(r.getAffiliate().getFullName());
        dto.setRegionName(r.getRegionName());
        dto.setCountry(r.getCountry());
        dto.setCity(r.getCity());
        dto.setRegionDescription(r.getRegionDescription());
        dto.setIsActive(r.getIsActive());
        dto.setIsPaid(r.getIsPaid());
        dto.setPricePaid(r.getPricePaid());
        dto.setIsPremium(r.getIsPremium());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());
        return dto;
    }

    private AffiliateTransactionDTO toTransactionDTO(AffiliateTransaction t) {
        AffiliateTransactionDTO dto = new AffiliateTransactionDTO();
        dto.setId(t.getId());
        dto.setAffiliateId(t.getAffiliate().getId());
        dto.setAffiliateName(t.getAffiliate().getFullName());
        dto.setPropertyId(t.getProperty().getId());
        dto.setPropertyTitle(t.getProperty().getTitre());
        dto.setTransactionDate(t.getTransactionDate());
        dto.setPropertyPrice(t.getPropertyPrice());
        dto.setCommissionPercentage(t.getCommissionPercentage());
        dto.setCommissionAmount(t.getCommissionAmount());
        dto.setTransactionType(t.getTransactionType());
        dto.setClientEmail(t.getClientEmail());
        dto.setIsPaid(t.getIsPaid());
        dto.setPaymentDate(t.getPaymentDate());
        return dto;
    }

    private AffiliateActivityDTO toActivityDTO(AffiliateActivity a) {
        AffiliateActivityDTO dto = new AffiliateActivityDTO();
        dto.setId(a.getId());
        dto.setAffiliateId(a.getAffiliate().getId());
        dto.setAffiliateName(a.getAffiliate().getFullName());
        dto.setActivityType(a.getActivityType());
        dto.setPropertyId(a.getPropertyId());
        if (a.getPropertyId() != null) {
            propertyRepository.findById(a.getPropertyId())
                    .ifPresent(p -> dto.setPropertyTitle(p.getTitre()));
        }
        dto.setActivityDate(a.getActivityDate());
        dto.setMetadata(a.getMetadata());
        return dto;
    }

    private AffiliatePropertyDTO toAffiliatePropertyDTO(Property p) {
        AffiliatePropertyDTO dto = new AffiliatePropertyDTO();
        dto.setId(p.getId());
        dto.setTitre(p.getTitre());
        dto.setType(p.getType());
        dto.setStatut(p.getStatut());
        dto.setPrixVente(p.getPrixVente());
        dto.setPrixLocation(p.getPrixLocation());
        dto.setSurface(p.getSurface());
        dto.setNbChambres(p.getNbChambres());
        dto.setAdresse(p.getAdresse());
        dto.setCity(p.getCity());
        dto.setRegion(p.getRegion());
        dto.setLatitude(p.getLatitude());
        dto.setLongitude(p.getLongitude());
        dto.setCommissionPercentage(p.getCommissionPercentage());
        dto.setCommissionType(p.getCommissionType());
        dto.setCommissionAmount(p.calculateCommissionAmount());
        dto.setHasMainImage(p.getMainImageId() != null);
        if (p.getMainImageId() != null) {
            dto.setMainImageUrl("/api/images/public/" + p.getMainImageId());
        }
        dto.setReservedByAffiliate(Boolean.TRUE.equals(p.getIsReservedByAffiliate()));
        return dto;
    }

    // ── Suggested expansion zones ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SuggestedZoneDTO> getSuggestedZones(Long affiliateId) {
        try {
            boolean isActive = affiliateProfileRepository.findByUserId(affiliateId)
                    .map(p -> p.getStatus() == AffiliateStatus.ACTIVE)
                    .orElse(false);
            if (!isActive) return new java.util.ArrayList<>();

            // Affiliate's current zone keys: country|city OR fallback to single name
            java.util.Set<String> myZoneKeys = new java.util.HashSet<>();
            for (AffiliateRegion r : affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(affiliateId)) {
                String key = buildZoneKey(r.getCountry(), r.getCity());
                if (key == null && r.getRegionName() != null) {
                    key = r.getRegionName().trim().toLowerCase();
                }
                if (key != null && !key.isBlank()) myZoneKeys.add(key);
            }

            List<Property> allEligible = propertyRepository.findAllAffiliateEligibleProperties();
            if (allEligible == null || allEligible.isEmpty()) return new java.util.ArrayList<>();

            // Demand map keyed by country|city (or city alone if country missing)
            java.util.Map<String, Long> demandByZone = new java.util.HashMap<>();
            for (com.immobilier.backend.entity.SaleOffer o : saleOfferRepository.findAcceptedOrCompletedOffers()) {
                if (o.getProperty() == null) continue;
                String key = buildZoneKey(o.getProperty().getCountry(), o.getProperty().getCity());
                if (key == null) continue;
                demandByZone.merge(key, 1L, Long::sum);
            }

            // Group eligible properties by country|city, excluding affiliate's current zones
            java.util.Map<String, List<Property>> byZone = new java.util.LinkedHashMap<>();
            for (Property p : allEligible) {
                String key = buildZoneKey(p.getCountry(), p.getCity());
                if (key == null) continue;
                if (myZoneKeys.contains(key)) continue;
                // Also exclude if affiliate had a legacy single-name zone matching the city
                if (p.getCity() != null && myZoneKeys.contains(p.getCity().trim().toLowerCase())) continue;
                byZone.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(p);
            }

            // Determine if the affiliate already has a free zone (for price calculation)
            boolean affiliateHasAnyZone = !myZoneKeys.isEmpty();

            List<SuggestedZoneDTO> suggestions = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, List<Property>> entry : byZone.entrySet()) {
                List<Property> props = entry.getValue();
                if (props.isEmpty()) continue;

                double avgCommission = props.stream()
                        .mapToDouble(pp -> pp.getCommissionPercentage() != null ? pp.getCommissionPercentage() : 0)
                        .average().orElse(0);
                double avgPrice = props.stream()
                        .mapToDouble(pp -> pp.getPrixVente() != null ? pp.getPrixVente()
                                : (pp.getPrixLocation() != null ? pp.getPrixLocation() : 0))
                        .average().orElse(0);

                Property first = props.get(0);
                String displayName = first.getCity() != null && !first.getCity().isBlank()
                        ? first.getCity()
                        : (first.getRegion() != null ? first.getRegion() : "Zone inconnue");
                String country = first.getCountry();
                String city    = first.getCity();
                int demandScore = demandByZone.getOrDefault(entry.getKey(), 0L).intValue();
                double score = props.size() * avgCommission + demandScore * 10.0;
                boolean premium = demandScore >= PREMIUM_DEMAND_THRESHOLD;
                double zonePrice = affiliateHasAnyZone
                        ? (premium ? PREMIUM_ZONE_PRICE : STANDARD_ZONE_PRICE)
                        : 0.0;

                SuggestedZoneDTO dto = new SuggestedZoneDTO();
                dto.setZoneName(displayName);
                dto.setCountry(country);
                dto.setCity(city);
                dto.setPropertyCount(props.size());
                dto.setAverageCommission(Math.round(avgCommission * 100.0) / 100.0);
                dto.setAveragePrice(Math.round(avgPrice));
                dto.setDemandScore(demandScore);
                dto.setOpportunityScore(score);
                dto.setPrice(zonePrice);
                dto.setPremium(premium);
                suggestions.add(dto);
            }

            suggestions.sort((a, b) -> Double.compare(b.getOpportunityScore(), a.getOpportunityScore()));
            return suggestions.stream().limit(5).collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to compute suggested zones for affiliateId={}", affiliateId, e);
            return new java.util.ArrayList<>();
        }
    }

    /** Builds a normalized zone key "country|city" lowercase, or null if either is missing. */
    private String buildZoneKey(String country, String city) {
        if (country == null || country.isBlank()) return null;
        if (city == null || city.isBlank()) return null;
        return country.trim().toLowerCase() + "|" + city.trim().toLowerCase();
    }
}
