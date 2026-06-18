package com.immobilier.backend.service;

import com.immobilier.backend.dto.CreateSaleOfferRequest;
import com.immobilier.backend.dto.RespondSaleOfferRequest;
import com.immobilier.backend.dto.SaleOfferDTO;
import com.immobilier.backend.entity.*;
import com.immobilier.backend.enums.AffiliateStatus;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.enums.SaleOfferStatus;
import com.immobilier.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOfferService {

    private final SaleOfferRepository saleOfferRepository;
    private final AffiliateProfileRepository affiliateProfileRepository;
    private final AffiliateRegionRepository affiliateRegionRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final AffiliateTransactionRepository affiliateTransactionRepository;
    private final ClientInfoRepository clientInfoRepository;
    private final AffiliateCustomerRelationRepository affiliateCustomerRelationRepository;
    private final NotificationService notificationService;

    // ── Affiliate: submit offer ───────────────────────────────────────────────

    @Transactional
    public SaleOfferDTO submitOffer(Long affiliateId, CreateSaleOfferRequest request) {
        // 1. Affiliate must be active
        AffiliateProfile profile = affiliateProfileRepository.findByUserId(affiliateId)
                .orElseThrow(() -> new RuntimeException("Profil affilié introuvable"));
        if (profile.getStatus() != AffiliateStatus.ACTIVE) {
            throw new RuntimeException("Votre compte affilié n'est pas actif");
        }

        // 2. Property must be eligible
        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new RuntimeException("Bien introuvable"));
        if (!Boolean.TRUE.equals(property.getIsAffiliateEligible())) {
            throw new RuntimeException("Ce bien n'est pas disponible pour les affiliés");
        }
        if (!Boolean.TRUE.equals(property.getIsActive()) || !"DISPONIBLE".equals(property.getStatut())) {
            throw new RuntimeException("Ce bien n'est pas disponible à la vente");
        }
        if (Boolean.TRUE.equals(property.getIsReservedByAffiliate())) {
            throw new RuntimeException("Ce bien est déjà réservé par un autre affilié");
        }

        // 3. Affiliate's zone must cover the property — strict country+city when available, legacy name otherwise
        List<AffiliateRegion> regions = affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(affiliateId);
        boolean inZone = regions.stream().anyMatch(r -> matchesAffiliateZone(r, property));
        if (!inZone) {
            throw new RuntimeException("Ce bien n'est pas dans votre zone d'affiliation");
        }

        // 4. No duplicate PENDING offer
        if (saleOfferRepository.existsByAffiliateIdAndPropertyIdAndStatus(
                affiliateId, request.getPropertyId(), SaleOfferStatus.PENDING)) {
            throw new RuntimeException("Vous avez déjà une offre en attente sur ce bien");
        }

        // 5. Build the offer
        SaleOffer offer = new SaleOffer();
        offer.setAffiliate(profile.getUser());
        offer.setProperty(property);
        offer.setStatus(SaleOfferStatus.PENDING);
        offer.setBuyerName(request.getBuyerName());
        offer.setBuyerEmail(request.getBuyerEmail());
        offer.setBuyerPhone(request.getBuyerPhone());
        offer.setOfferedPrice(request.getOfferedPrice());
        offer.setMessage(request.getMessage());

        SaleOffer saved = saleOfferRepository.save(offer);

        // 6. Notify the property owner
        notifyPropertyOwner(property, profile.getUser(), saved.getId());

        log.info("Sale offer {} submitted by affiliate {} on property {}", saved.getId(), affiliateId, property.getId());
        return toDTO(saved);
    }

    // ── Agency / Super Admin: respond to offer ────────────────────────────────

    @Transactional
    public SaleOfferDTO respondToOffer(Long offerId, RespondSaleOfferRequest request, Long responderId) {
        SaleOffer offer = getOfferOrThrow(offerId);

        if (offer.getStatus() != SaleOfferStatus.PENDING) {
            throw new RuntimeException("Seules les offres PENDING peuvent être répondues");
        }

        User responder = userRepository.findById(responderId)
                .orElseThrow(() -> new RuntimeException("Répondant introuvable"));

        // Check that responder is allowed (owns the property or is SUPER_ADMIN)
        assertResponderHasAccess(responder, offer.getProperty());

        offer.setRespondedBy(responder);
        offer.setRespondedAt(LocalDateTime.now());

        if ("ACCEPTED".equals(request.getResponse())) {
            offer.setStatus(SaleOfferStatus.ACCEPTED);
            snapshotCommission(offer);

            // Reserve the property and mark it EN_ATTENTE so no manual edits can override it
            Property property = offer.getProperty();
            property.setIsReservedByAffiliate(true);
            property.setStatut("EN_ATTENTE");
            propertyRepository.save(property);

            notificationService.create(
                offer.getAffiliate(),
                NotificationType.SALE_OFFER_ACCEPTED,
                "Offre de vente acceptée",
                "Votre offre pour le bien « " + offer.getProperty().getTitre() + " » a été acceptée.",
                offerId
            );

            // Auto-reject all sibling PENDING offers on the same property and notify their affiliates
            autoRejectSiblingOffers(property.getId(), offer.getId(), property.getTitre());

        } else if ("REJECTED".equals(request.getResponse())) {
            if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
                throw new RuntimeException("La raison du rejet est obligatoire");
            }
            offer.setStatus(SaleOfferStatus.REJECTED);
            offer.setRejectionReason(request.getRejectionReason());

            notificationService.create(
                offer.getAffiliate(),
                NotificationType.SALE_OFFER_REJECTED,
                "Offre de vente refusée",
                "Votre offre pour le bien « " + offer.getProperty().getTitre() + " » a été refusée : " + request.getRejectionReason(),
                offerId
            );
        }

        return toDTO(saleOfferRepository.save(offer));
    }

    // ── Agency / Super Admin: complete offer (finalise sale) ──────────────────

    @Transactional
    public SaleOfferDTO completeOffer(Long offerId, Long completedById) {
        SaleOffer offer = getOfferOrThrow(offerId);

        if (offer.getStatus() != SaleOfferStatus.ACCEPTED) {
            throw new RuntimeException("Seules les offres ACCEPTED peuvent être complétées");
        }

        User completedBy = userRepository.findById(completedById)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        assertResponderHasAccess(completedBy, offer.getProperty());

        offer.setStatus(SaleOfferStatus.COMPLETED);
        saleOfferRepository.save(offer);

        // Convert the property to its terminal sold/rented status so it disappears
        // from all property listings (including Gestion des propriétés).
        Property property = offer.getProperty();
        String terminalStatus = property.getPrixVente() != null ? "VENDU" : "LOUE";
        property.setStatut(terminalStatus);
        property.setIsReservedByAffiliate(true);
        if ("VENDU".equals(terminalStatus)) {
            property.setIsFinalized(true);
        }
        propertyRepository.save(property);

        // Record the affiliate transaction
        AffiliateTransaction tx = new AffiliateTransaction();
        tx.setAffiliate(offer.getAffiliate());
        tx.setProperty(offer.getProperty());
        tx.setTransactionDate(LocalDateTime.now());
        tx.setPropertyPrice(offer.getOfferedPrice() != null
                ? offer.getOfferedPrice()
                : (offer.getProperty().getPrixVente() != null
                    ? offer.getProperty().getPrixVente()
                    : offer.getProperty().getPrixLocation()));
        tx.setCommissionPercentage(offer.getCommissionPercentage());
        tx.setCommissionAmount(offer.getCommissionAmount());
        tx.setTransactionType(offer.getProperty().getPrixVente() != null ? "SALE" : "RENT");
        tx.setClientEmail(offer.getBuyerEmail());
        tx.setIsPaid(false);
        affiliateTransactionRepository.save(tx);

        // Persist the affiliate → buyer CRM relation (NO User account is ever
        // created for the buyer). Idempotent: one relation per SaleOffer.
        try {
            if (!affiliateCustomerRelationRepository.existsBySaleOfferId(offer.getId())) {
                AffiliateCustomerRelation rel = new AffiliateCustomerRelation();
                rel.setAffiliate(offer.getAffiliate());
                rel.setBuyerName(offer.getBuyerName());
                rel.setBuyerEmail(offer.getBuyerEmail());
                rel.setBuyerPhone(offer.getBuyerPhone());
                rel.setProperty(offer.getProperty());
                rel.setSaleOffer(offer);
                rel.setAffiliateTransaction(tx);
                rel.setTransactionType(tx.getTransactionType());
                rel.setPropertyPrice(tx.getPropertyPrice());
                rel.setCommissionAmount(tx.getCommissionAmount());
                affiliateCustomerRelationRepository.save(rel);
            }
        } catch (Exception e) {
            log.warn("Could not persist AffiliateCustomerRelation for offer {}: {}", offer.getId(), e.getMessage());
        }

        // Keep ClientInfo counters in sync so the client-management list reflects
        // the new sale count immediately without requiring a full stats reload.
        try {
            List<ClientInfo> infos = clientInfoRepository.findAllByUserId(offer.getAffiliate().getId());
            for (ClientInfo info : infos) {
                info.setNombreVentesLiees((info.getNombreVentesLiees() == null ? 0 : info.getNombreVentesLiees()) + 1);
                double addedComm = tx.getCommissionAmount() != null ? tx.getCommissionAmount() : 0.0;
                info.setCommissionGeneree((info.getCommissionGeneree() == null ? 0.0 : info.getCommissionGeneree()) + addedComm);
                clientInfoRepository.save(info);
            }
        } catch (Exception e) {
            log.warn("Could not sync ClientInfo counters for affiliate {}: {}", offer.getAffiliate().getId(), e.getMessage());
        }

        notificationService.create(
            offer.getAffiliate(),
            NotificationType.SALE_OFFER_COMPLETED,
            "Vente finalisée",
            "La vente du bien « " + offer.getProperty().getTitre() + " » est finalisée. Commission : "
                + String.format("%.2f TND", offer.getCommissionAmount()),
            offerId
        );

        log.info("Offer {} completed, transaction recorded for affiliate {}", offerId, offer.getAffiliate().getId());
        return toDTO(offer);
    }

    // ── Affiliate: cancel offer ───────────────────────────────────────────────

    @Transactional
    public SaleOfferDTO cancelOffer(Long offerId, Long affiliateId) {
        SaleOffer offer = getOfferOrThrow(offerId);

        if (!offer.getAffiliate().getId().equals(affiliateId)) {
            throw new RuntimeException("Accès refusé");
        }
        if (offer.getStatus() != SaleOfferStatus.PENDING) {
            throw new RuntimeException("Seules les offres PENDING peuvent être annulées");
        }

        offer.setStatus(SaleOfferStatus.CANCELLED);
        return toDTO(saleOfferRepository.save(offer));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SaleOfferDTO> getMyOffers(Long affiliateId) {
        return saleOfferRepository.findByAffiliateIdOrderByCreatedAtDesc(affiliateId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SaleOfferDTO> getIncomingOffersForAgency(Long agencyAdminId) {
        return saleOfferRepository.findByAgencyAdminIdOrderByCreatedAtDesc(agencyAdminId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SaleOfferDTO> getIncomingPendingForAgency(Long agencyAdminId) {
        return saleOfferRepository.findByAgencyAdminIdAndStatusOrderByCreatedAtDesc(agencyAdminId, SaleOfferStatus.PENDING)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SaleOfferDTO> getAllOffers() {
        return saleOfferRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SaleOfferDTO getOfferById(Long offerId) {
        return toDTO(getOfferOrThrow(offerId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SaleOffer getOfferOrThrow(Long offerId) {
        return saleOfferRepository.findById(offerId)
                .orElseThrow(() -> new RuntimeException("Offre introuvable: " + offerId));
    }

    private void assertResponderHasAccess(User responder, Property property) {
        if (responder.getRole() == RoleType.SUPER_ADMIN) return;
        if (responder.getRole() == RoleType.ADMIN
                && property.getAgencyAdmin() != null
                && property.getAgencyAdmin().getId().equals(responder.getId())) return;
        // RESPONSABLE_COMMERCIAL / COMMERCIAL under the agency admin
        if ((responder.getRole() == RoleType.RESPONSABLE_COMMERCIAL || responder.getRole() == RoleType.COMMERCIAL)
                && property.getAgencyAdmin() != null
                && isUnderAdmin(responder, property.getAgencyAdmin().getId())) return;

        throw new RuntimeException("Accès refusé: vous n'êtes pas propriétaire de ce bien");
    }

    private boolean isUnderAdmin(User user, Long adminId) {
        User current = user.getParent();
        while (current != null) {
            if (current.getId().equals(adminId)) return true;
            current = current.getParent();
        }
        return false;
    }

    /**
     * Strict zone match between an affiliate's region and a property:
     *  - If the region carries explicit country + city, BOTH must equal property.country and property.city.
     *  - Otherwise (legacy regionName-only entry), fall back to matching the name against property.city or property.region.
     * All comparisons are trimmed and case-insensitive.
     */
    private boolean matchesAffiliateZone(AffiliateRegion region, Property property) {
        if (region == null || property == null) return false;

        String rCountry = region.getCountry();
        String rCity    = region.getCity();
        boolean hasStrict = rCountry != null && !rCountry.isBlank()
                         && rCity    != null && !rCity.isBlank();

        if (hasStrict) {
            String pCountry = property.getCountry();
            String pCity    = property.getCity();
            if (pCountry == null || pCity == null) return false;
            return pCountry.trim().equalsIgnoreCase(rCountry.trim())
                && pCity.trim().equalsIgnoreCase(rCity.trim());
        }

        // Legacy fallback: single regionName matches city or region
        String name = region.getRegionName();
        if (name == null || name.isBlank() || name.contains(",")) return false;
        String n = name.trim();
        return (property.getCity()   != null && property.getCity().trim().equalsIgnoreCase(n))
            || (property.getRegion() != null && property.getRegion().trim().equalsIgnoreCase(n));
    }

    /** Auto-reject every other PENDING offer on the same property and notify each affiliate. */
    private void autoRejectSiblingOffers(Long propertyId, Long acceptedOfferId, String propertyTitle) {
        List<SaleOffer> all = saleOfferRepository.findAllByOrderByCreatedAtDesc();
        for (SaleOffer sibling : all) {
            if (sibling.getId().equals(acceptedOfferId)) continue;
            if (sibling.getProperty() == null || !propertyId.equals(sibling.getProperty().getId())) continue;
            if (sibling.getStatus() != SaleOfferStatus.PENDING) continue;

            sibling.setStatus(SaleOfferStatus.REJECTED);
            sibling.setRejectionReason("Une autre offre a été retenue pour ce bien.");
            sibling.setRespondedAt(LocalDateTime.now());
            saleOfferRepository.save(sibling);

            notificationService.create(
                sibling.getAffiliate(),
                NotificationType.SALE_OFFER_REJECTED,
                "Offre de vente refusée",
                "Votre offre pour le bien « " + propertyTitle + " » a été refusée car une autre offre a été retenue.",
                sibling.getId()
            );
            log.info("Auto-rejected sibling offer {} on property {}", sibling.getId(), propertyId);
        }
    }

    private void snapshotCommission(SaleOffer offer) {
        Property p = offer.getProperty();
        double commPct = p.getCommissionPercentage() != null ? p.getCommissionPercentage() : 0.0;
        offer.setCommissionPercentage(commPct);
        offer.setCommissionAmount(p.calculateCommissionAmount());
    }

    private void notifyPropertyOwner(Property property, User affiliate, Long offerId) {
        if (property.getAgencyAdmin() != null) {
            notificationService.create(
                property.getAgencyAdmin(),
                NotificationType.SALE_OFFER_RECEIVED,
                "Nouvelle offre de vente affilié",
                "L'affilié " + affiliate.getFullName() + " a soumis une offre pour le bien « " + property.getTitre() + " ».",
                offerId
            );
        } else {
            // SUPER_ADMIN_OWNED
            userRepository.findByRole(RoleType.SUPER_ADMIN).forEach(sa ->
                notificationService.create(
                    sa,
                    NotificationType.SALE_OFFER_RECEIVED,
                    "Nouvelle offre de vente affilié",
                    "L'affilié " + affiliate.getFullName() + " a soumis une offre pour le bien « " + property.getTitre() + " ».",
                    offerId
                )
            );
        }
    }

    // ── DTO converter ─────────────────────────────────────────────────────────

    private SaleOfferDTO toDTO(SaleOffer o) {
        SaleOfferDTO dto = new SaleOfferDTO();
        dto.setId(o.getId());

        dto.setAffiliateId(o.getAffiliate().getId());
        dto.setAffiliateName(o.getAffiliate().getFullName());
        dto.setAffiliateEmail(o.getAffiliate().getEmail());

        Property p = o.getProperty();
        dto.setPropertyId(p.getId());
        dto.setPropertyTitle(p.getTitre());
        dto.setPropertyAdresse(p.getAdresse());
        dto.setPropertyCity(p.getCity());
        dto.setPropertyPrixVente(p.getPrixVente());
        dto.setPropertyPrixLocation(p.getPrixLocation());
        dto.setPropertyCommissionPercentage(p.getCommissionPercentage());
        dto.setPropertyCommissionType(p.getCommissionType());
        if (p.getMainImageId() != null) {
            dto.setMainImageUrl("/api/images/public/" + p.getMainImageId());
        }

        dto.setBuyerName(o.getBuyerName());
        dto.setBuyerEmail(o.getBuyerEmail());
        dto.setBuyerPhone(o.getBuyerPhone());
        dto.setOfferedPrice(o.getOfferedPrice());
        dto.setMessage(o.getMessage());

        dto.setStatus(o.getStatus());
        dto.setRejectionReason(o.getRejectionReason());
        dto.setRespondedAt(o.getRespondedAt());
        if (o.getRespondedBy() != null) {
            dto.setRespondedById(o.getRespondedBy().getId());
            dto.setRespondedByName(o.getRespondedBy().getFullName());
        }

        dto.setCommissionPercentage(o.getCommissionPercentage());
        dto.setCommissionAmount(o.getCommissionAmount());

        dto.setCreatedAt(o.getCreatedAt());
        dto.setUpdatedAt(o.getUpdatedAt());
        return dto;
    }
}
