package com.immobilier.backend.service;

import com.immobilier.backend.dto.ZonePaymentRequestDTO;
import com.immobilier.backend.entity.*;
import com.immobilier.backend.enums.AffiliateStatus;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZonePaymentService {

    private final ZonePaymentRepository zonePaymentRepository;
    private final AffiliateProfileRepository affiliateProfileRepository;
    private final AffiliateRegionRepository affiliateRegionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${file.upload.payments-dir:uploads/payments}")
    private String paymentsDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final int MAX_ZONES = 3;
    private static final double STANDARD_PRICE = 50.0;
    private static final double PREMIUM_PRICE  = 100.0;

    // ── Affiliate: submit zone payment request ────────────────────────────────

    @Transactional
    public ZonePaymentRequestDTO submitRequest(Long affiliateId, String country, String city,
                                               boolean isPremium, MultipartFile proofImage) {
        // 1. Affiliate must be active
        AffiliateProfile profile = affiliateProfileRepository.findByUserId(affiliateId)
                .orElseThrow(() -> new RuntimeException("Profil affilié introuvable"));
        if (profile.getStatus() != AffiliateStatus.ACTIVE) {
            throw new RuntimeException("Votre compte affilié n'est pas actif");
        }

        User affiliate = profile.getUser();

        // 2. Zone limit check
        List<AffiliateRegion> existing = affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(affiliateId);
        if (existing.size() >= MAX_ZONES) {
            throw new RuntimeException("Vous avez atteint le maximum de " + MAX_ZONES + " zones actives.");
        }

        // 3. Duplicate active zone check
        boolean alreadyActive = existing.stream().anyMatch(r ->
                country.trim().equalsIgnoreCase(r.getCountry() != null ? r.getCountry().trim() : "") &&
                city.trim().equalsIgnoreCase(r.getCity() != null ? r.getCity().trim() : ""));
        if (alreadyActive) {
            throw new RuntimeException("Cette zone est déjà active dans votre compte.");
        }

        // 4. Duplicate pending request check
        if (!zonePaymentRepository.findPendingForAffiliateAndZone(affiliateId, country, city).isEmpty()) {
            throw new RuntimeException("Vous avez déjà une demande en attente pour cette zone.");
        }

        // 5. Proof image required
        if (proofImage == null || proofImage.isEmpty()) {
            throw new RuntimeException("La preuve de paiement est obligatoire.");
        }

        // 6. Save proof image
        String proofPath = saveProofImage(proofImage, affiliateId);

        // 7. Determine amount
        double amount = isPremium ? PREMIUM_PRICE : STANDARD_PRICE;
        String displayName = city + ", " + country;

        ZonePaymentRequest req = new ZonePaymentRequest();
        req.setAffiliate(affiliate);
        req.setCountry(country.trim());
        req.setCity(city.trim());
        req.setZoneName(displayName);
        req.setAmount(amount);
        req.setIsPremium(isPremium);
        req.setProofImagePath(proofPath);
        req.setStatus("PENDING");

        ZonePaymentRequest saved = zonePaymentRepository.save(req);

        // 8. Notify all Super Admins
        userRepository.findByRole(RoleType.SUPER_ADMIN).forEach(sa ->
            notificationService.create(
                sa,
                NotificationType.ZONE_PAYMENT_SUBMITTED,
                "Preuve de paiement zone",
                "L'affilié " + affiliate.getFullName() + " a soumis une preuve de paiement pour la zone « "
                        + displayName + " » (" + (int) amount + " TND).",
                saved.getId()
            )
        );

        log.info("Zone payment request {} submitted by affiliate {}", saved.getId(), affiliateId);
        return toDTO(saved);
    }

    // ── Affiliate: own requests ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ZonePaymentRequestDTO> getMyRequests(Long affiliateId) {
        return zonePaymentRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── Super Admin: all requests ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ZonePaymentRequestDTO> getAllRequests() {
        return zonePaymentRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ZonePaymentRequestDTO> getPendingRequests() {
        return zonePaymentRepository.findByStatusOrderByCreatedAtDesc("PENDING")
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── Super Admin: approve ──────────────────────────────────────────────────

    @Transactional
    public ZonePaymentRequestDTO approveRequest(Long requestId, Long reviewerId) {
        ZonePaymentRequest req = getOrThrow(requestId);
        if (!"PENDING".equals(req.getStatus())) {
            throw new RuntimeException("Seules les demandes PENDING peuvent être approuvées.");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        req.setStatus("APPROVED");
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());
        zonePaymentRepository.save(req);

        // Activate the zone
        activateZone(req);

        // Notify affiliate
        notificationService.create(
            req.getAffiliate(),
            NotificationType.ZONE_PAYMENT_APPROVED,
            "Zone activée !",
            "Votre paiement pour la zone « " + req.getZoneName() + " » a été validé. La zone est maintenant active.",
            req.getId()
        );

        log.info("Zone payment request {} approved, zone {} activated for affiliate {}",
                requestId, req.getZoneName(), req.getAffiliate().getId());
        return toDTO(req);
    }

    // ── Super Admin: reject ───────────────────────────────────────────────────

    @Transactional
    public ZonePaymentRequestDTO rejectRequest(Long requestId, Long reviewerId, String reason) {
        ZonePaymentRequest req = getOrThrow(requestId);
        if (!"PENDING".equals(req.getStatus())) {
            throw new RuntimeException("Seules les demandes PENDING peuvent être rejetées.");
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("La raison du rejet est obligatoire.");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        req.setStatus("REJECTED");
        req.setRejectionReason(reason.trim());
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());
        zonePaymentRepository.save(req);

        // Notify affiliate
        notificationService.create(
            req.getAffiliate(),
            NotificationType.ZONE_PAYMENT_REJECTED,
            "Paiement de zone refusé",
            "Votre demande pour la zone « " + req.getZoneName() + " » a été refusée : " + reason,
            req.getId()
        );

        log.info("Zone payment request {} rejected for affiliate {}", requestId, req.getAffiliate().getId());
        return toDTO(req);
    }

    // ── Proof image serving ───────────────────────────────────────────────────

    public byte[] getProofImage(String filename) throws IOException {
        Path path = Paths.get(paymentsDir).resolve(filename).normalize();
        if (!path.startsWith(Paths.get(paymentsDir).normalize())) {
            throw new RuntimeException("Accès refusé");
        }
        return Files.readAllBytes(path);
    }

    public String getContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf"))  return "application/pdf";
        return "image/jpeg";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void activateZone(ZonePaymentRequest req) {
        AffiliateRegion region = new AffiliateRegion();
        region.setAffiliate(req.getAffiliate());
        region.setRegionName(req.getCity().trim().toLowerCase());
        region.setCountry(req.getCountry());
        region.setCity(req.getCity());
        region.setRegionDescription(req.getCountry());
        region.setIsActive(true);
        region.setIsPaid(true);
        region.setPricePaid(req.getAmount());
        region.setIsPremium(Boolean.TRUE.equals(req.getIsPremium()));
        affiliateRegionRepository.save(region);
    }

    private String saveProofImage(MultipartFile file, Long affiliateId) {
        try {
            Path dir = Paths.get(paymentsDir);
            Files.createDirectories(dir);

            String ext = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf("."));
            }
            String filename = "proof_" + affiliateId + "_" + UUID.randomUUID() + ext;
            Path target = dir.resolve(filename);
            Files.write(target, file.getBytes());
            return filename;
        } catch (IOException e) {
            log.error("Failed to save proof image for affiliate {}", affiliateId, e);
            throw new RuntimeException("Impossible de sauvegarder la preuve de paiement.");
        }
    }

    private ZonePaymentRequest getOrThrow(Long id) {
        return zonePaymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande de paiement introuvable: " + id));
    }

    public ZonePaymentRequestDTO toDTO(ZonePaymentRequest r) {
        ZonePaymentRequestDTO dto = new ZonePaymentRequestDTO();
        dto.setId(r.getId());
        dto.setAffiliateId(r.getAffiliate().getId());
        dto.setAffiliateName(r.getAffiliate().getFullName());
        dto.setAffiliateEmail(r.getAffiliate().getEmail());
        dto.setCountry(r.getCountry());
        dto.setCity(r.getCity());
        dto.setZoneName(r.getZoneName());
        dto.setAmount(r.getAmount());
        dto.setIsPremium(r.getIsPremium());
        dto.setStatus(r.getStatus());
        dto.setRejectionReason(r.getRejectionReason());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setReviewedAt(r.getReviewedAt());
        if (r.getReviewedBy() != null) dto.setReviewedByName(r.getReviewedBy().getFullName());
        if (r.getProofImagePath() != null) {
            dto.setProofImageUrl(baseUrl + "/api/zone-payments/proof/" + r.getProofImagePath());
        }
        return dto;
    }
}
