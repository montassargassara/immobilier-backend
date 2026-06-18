package com.immobilier.backend.service;

import com.immobilier.backend.dto.ConvertLeadRequest;
import com.immobilier.backend.dto.InterestRequestCreateRequest;
import com.immobilier.backend.dto.InterestRequestDTO;
import com.immobilier.backend.entity.ClientInfo;
import com.immobilier.backend.entity.InterestRequest;
import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.ClientInfoRepository;
import com.immobilier.backend.repository.InterestRequestRepository;
import com.immobilier.backend.repository.PropertyRepository;
import com.immobilier.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CRM lead lifecycle service.
 *
 * Terminal states: CONVERTI_VENTE, CONVERTI_LOCATION, REFUSE.
 * Once a lead reaches any of these states it is locked — no further status changes.
 *
 * Multi-client rule: when a lead is converted the service auto-refuses all sibling
 * active leads on the same property and notifies each losing client.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestRequestService {

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("CONVERTI_VENTE", "CONVERTI_LOCATION", "REFUSE");

    private final InterestRequestRepository interestRequestRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final ClientInfoRepository clientInfoRepository;
    private final NotificationService notificationService;
    private final SaleValidationService saleValidationService;
    private final CommissionService commissionService;

    // ─── Public client: submit interest ──────────────────────────────────────

    @Transactional
    public InterestRequestDTO submit(User publicClient, InterestRequestCreateRequest req) {
        Property property = propertyRepository.findById(req.getPropertyId())
                .orElseThrow(() -> new IllegalArgumentException("Propriété introuvable"));
        if (Boolean.FALSE.equals(property.getIsActive())) {
            throw new IllegalArgumentException("Cette annonce n'est plus disponible");
        }

        InterestRequest interest = new InterestRequest();
        interest.setUser(publicClient);
        interest.setProperty(property);
        interest.setOwnerUser(resolveOwner(property));
        interest.setFullName(req.getFullName());
        interest.setEmail(publicClient.getEmail());
        interest.setTelephone(req.getTelephone());
        interest.setMessage(req.getMessage());
        interest.setProposedBudget(req.getProposedBudget());
        interest.setStatus("PENDING");
        interest.setLocked(false);
        interest = interestRequestRepository.save(interest);

        if (property.getAgencyAdmin() != null) {
            ensureAgencyCrmLead(publicClient, property.getAgencyAdmin());
        }

        notifyOwner(interest);
        return toDTO(interest);
    }

    // ─── Public client: list own interests ────────────────────────────────────

    public List<InterestRequestDTO> myInterests(User publicClient) {
        return interestRequestRepository.findByUserId(publicClient.getId()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ─── Admin: list leads where the caller is the property owner ─────────────

    public List<InterestRequestDTO> getInterestsForOwner(User owner) {
        return interestRequestRepository.findByOwnerUserId(owner.getId()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ─── Admin: simple status move (non-terminal) ──────────────────────────────

    /**
     * Moves a lead through non-terminal pipeline steps
     * (PENDING → CONTACTED → VISITE_PROGRAMMEE → EN_NEGOCIATION).
     * Terminal transitions (CONVERTI_* / REFUSE) must go through {@link #convertLead}.
     */
    @Transactional
    public InterestRequestDTO updateStatus(Long id, String newStatus, User requester) {
        InterestRequest ir = findAndAuthorize(id, requester);

        if (Boolean.TRUE.equals(ir.getLocked())) {
            throw new IllegalStateException("Ce lead est verrouillé et ne peut plus être modifié.");
        }
        if (TERMINAL_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException(
                    "Utilisez l'endpoint /convert pour les conversions et les refus.");
        }
        ir.setStatus(newStatus);
        return toDTO(interestRequestRepository.save(ir));
    }

    // ─── Admin: convert or refuse a lead ──────────────────────────────────────

    /**
     * Handles all terminal transitions:
     * <ul>
     *   <li>CONVERTI_VENTE → property statut=VENDU + auto-refuse siblings</li>
     *   <li>CONVERTI_LOCATION → property statut=LOUE + rental contract + auto-refuse siblings</li>
     *   <li>REFUSE → lock lead, notify client, archive</li>
     * </ul>
     */
    @Transactional
    public InterestRequestDTO convertLead(Long id, ConvertLeadRequest req, User requester) {
        InterestRequest ir = findAndAuthorize(id, requester);

        if (Boolean.TRUE.equals(ir.getLocked())) {
            throw new IllegalStateException("Ce lead est déjà verrouillé.");
        }

        String target = req.getTargetStatus();
        if (target == null || !TERMINAL_STATUSES.contains(target)) {
            throw new IllegalArgumentException("Statut cible invalide: " + target);
        }

        Property property = ir.getProperty();

        // ── Cross-ownership guard for terminal sale/rental transitions ────────
        // REFUSE does not change property ownership — no validation needed.
        if (!"REFUSE".equals(target) && !saleValidationService.isPropertyOwner(requester, property)) {
            String rentalStart = req.getRentalStartDate() != null ? req.getRentalStartDate().toString() : null;
            saleValidationService.createForCrmLead(
                    property, ir, target,
                    rentalStart,
                    req.getRentalDurationMonths(),
                    req.getRentalAmount(),
                    req.getRentalNotes(),
                    requester);
            // Return the lead as-is (not locked yet — owner must approve first)
            return toDTO(ir);
        }

        switch (target) {
            case "CONVERTI_VENTE" -> handleConvertVente(ir, property);
            case "CONVERTI_LOCATION" -> handleConvertLocation(ir, property, req);
            case "REFUSE" -> handleRefuse(ir, req);
        }

        // Owner-path conversion (non-owner returned early via validation workflow above).
        // Record staff / agency commissions for the requester who closed the deal.
        if ("CONVERTI_VENTE".equals(target)) {
            commissionService.recordForCompletedSale(property, requester, "SALE");
        } else if ("CONVERTI_LOCATION".equals(target)) {
            commissionService.recordForCompletedSale(property, requester, "RENT");
        }

        ir.setStatus(target);
        ir.setLocked(true);
        ir.setLockedAt(LocalDateTime.now());
        InterestRequest saved = interestRequestRepository.save(ir);
        return toDTO(saved);
    }

    // ─── Internals ─────────────────────────────────────────────────────────────

    private void handleConvertVente(InterestRequest ir, Property property) {
        property.setStatut("VENDU");
        property.setIsFinalized(true);
        if (ir.getUser() != null) property.setBuyer(ir.getUser());
        propertyRepository.save(property);
        if (ir.getUser() != null) updateClientBuyerStats(ir.getUser(), property, "VENDU");
        log.info("Lead {} converted to VENTE — property {} set VENDU", ir.getId(), property.getId());

        autoRefuseSiblings(ir, property,
                "Un autre client a été retenu pour ce bien (vente conclue).");

        notifyOwner(ir, NotificationType.LEAD_CONVERTED_SALE,
                "Bien vendu",
                String.format("Le bien « %s » a été vendu via le lead de %s.",
                        property.getTitre(), ir.getFullName()));
    }

    private void handleConvertLocation(InterestRequest ir, Property property, ConvertLeadRequest req) {
        if (req.getRentalStartDate() == null || req.getRentalDurationMonths() == null
                || req.getRentalDurationMonths() < 1) {
            throw new IllegalArgumentException(
                    "La date de début et la durée sont obligatoires pour une location.");
        }

        LocalDate start = req.getRentalStartDate();
        LocalDate end   = start.plusMonths(req.getRentalDurationMonths());

        // Record rental contract on the lead
        ir.setRentalStartDate(start);
        ir.setRentalEndDate(end);
        ir.setRentalDurationMonths(req.getRentalDurationMonths());
        ir.setRentalAmount(req.getRentalAmount());
        ir.setRentalNotes(req.getRentalNotes());

        // Update property rental fields and status
        property.setStatut("LOUE");
        if (ir.getUser() != null) property.setBuyer(ir.getUser());
        property.setRentalStartDate(start.atStartOfDay());
        property.setRentalEndDate(end.atStartOfDay());
        property.setRentalDurationMonths(req.getRentalDurationMonths());
        propertyRepository.save(property);
        if (ir.getUser() != null) updateClientBuyerStats(ir.getUser(), property, "LOUE");
        log.info("Lead {} converted to LOCATION — property {} set LOUE until {}",
                ir.getId(), property.getId(), end);

        autoRefuseSiblings(ir, property,
                "Un autre client a été retenu pour ce bien (location conclue).");

        notifyOwner(ir, NotificationType.LEAD_CONVERTED_RENTAL,
                "Bien loué",
                String.format("Le bien « %s » a été loué via le lead de %s (jusqu'au %s).",
                        property.getTitre(), ir.getFullName(),
                        end.toString()));
    }

    private void handleRefuse(InterestRequest ir, ConvertLeadRequest req) {
        ir.setRejectionMessage(req.getRejectionMessage());

        // Notify the public client
        User client = ir.getUser();
        if (client != null) {
            String clientMsg = req.getRejectionMessage() != null && !req.getRejectionMessage().isBlank()
                    ? req.getRejectionMessage()
                    : "Votre demande d'intérêt pour ce bien n'a pas été retenue.";
            notificationService.create(client, NotificationType.LEAD_REFUSED,
                    "Demande d'intérêt refusée",
                    String.format("Votre intérêt pour « %s » : %s",
                            ir.getProperty().getTitre(), clientMsg),
                    ir.getId());
        }
        log.info("Lead {} REFUSE — client notified", ir.getId());
    }

    /**
     * Auto-refuses every active sibling lead on the same property, notifies each client.
     */
    private void autoRefuseSiblings(InterestRequest winner, Property property, String reason) {
        List<InterestRequest> siblings =
                interestRequestRepository.findActiveLeadsForProperty(property.getId(), winner.getId());

        for (InterestRequest sib : siblings) {
            sib.setStatus("REFUSE");
            sib.setLocked(true);
            sib.setLockedAt(LocalDateTime.now());
            sib.setRejectionMessage(reason);
            interestRequestRepository.save(sib);

            // Notify losing client
            User client = sib.getUser();
            if (client != null) {
                notificationService.create(client, NotificationType.LEAD_AUTO_REFUSED,
                        "Bien non disponible",
                        String.format("Le bien « %s » que vous convoitiez n'est plus disponible : %s",
                                property.getTitre(), reason),
                        sib.getId());
            }
        }
        log.info("Auto-refused {} sibling lead(s) for property {}", siblings.size(), property.getId());
    }

    private InterestRequest findAndAuthorize(Long id, User requester) {
        InterestRequest ir = interestRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable"));
        boolean isOwner = ir.getOwnerUser() != null
                && Objects.equals(ir.getOwnerUser().getId(), requester.getId());
        boolean isSuperAdmin = requester.getRole() != null
                && RoleType.SUPER_ADMIN.equals(requester.getRole());
        if (!isOwner && !isSuperAdmin) {
            throw new SecurityException("Accès refusé");
        }
        return ir;
    }

    private User resolveOwner(Property property) {
        if (property.getAgencyAdmin() != null) return property.getAgencyAdmin();
        return userRepository.findByRole(RoleType.SUPER_ADMIN).stream()
                .findFirst()
                .orElse(null);
    }

    private void ensureAgencyCrmLead(User publicClient, User agencyAdmin) {
        if (agencyAdmin == null) return;
        boolean exists = clientInfoRepository.existsByUserIdAndAgencyAdminId(
                publicClient.getId(), agencyAdmin.getId());
        if (exists) return;

        ClientInfo lead = new ClientInfo();
        lead.setUser(publicClient);
        lead.setCreatedBy(agencyAdmin);
        lead.setAgencyAdminId(agencyAdmin.getId());
        lead.setVisibilityType("AGENCY_CLIENT");
        lead.setSource("Portail public — Intéressé");
        clientInfoRepository.save(lead);
        log.info("Created agency CRM lead for public client {} in agency {}",
                publicClient.getId(), agencyAdmin.getId());
    }

    private void notifyOwner(InterestRequest interest) {
        User owner = interest.getOwnerUser();
        if (owner == null) return;
        Property p = interest.getProperty();
        notificationService.create(owner, NotificationType.PROPERTY_INTEREST_RECEIVED,
                "Nouveau prospect intéressé",
                String.format("%s est intéressé par « %s ».", interest.getFullName(), p.getTitre()),
                interest.getId());
    }

    private void notifyOwner(InterestRequest interest, NotificationType type,
                              String title, String message) {
        User owner = interest.getOwnerUser();
        if (owner == null) return;
        notificationService.create(owner, type, title, message, interest.getId());
    }

    private void updateClientBuyerStats(User buyer, Property property, String status) {
        try {
            boolean isVente = "VENDU".equals(status);
            double price = isVente
                    ? (property.getPrixVente() != null ? property.getPrixVente() : 0.0)
                    : (property.getPrixLocation() != null ? property.getPrixLocation() : 0.0);
            List<ClientInfo> rows = clientInfoRepository.findAllByUserId(buyer.getId());
            for (ClientInfo info : rows) {
                if (isVente) {
                    info.setNombreAchats((info.getNombreAchats() == null ? 0 : info.getNombreAchats()) + 1);
                } else {
                    info.setNombreLocations((info.getNombreLocations() == null ? 0 : info.getNombreLocations()) + 1);
                }
                info.setTotalAchats((info.getTotalAchats() == null ? 0.0 : info.getTotalAchats()) + price);
                clientInfoRepository.save(info);
            }
        } catch (Exception e) {
            log.warn("Could not update buyer stats for user {}: {}", buyer.getId(), e.getMessage());
        }
    }

    // ─── DTO mapping ──────────────────────────────────────────────────────────

    public InterestRequestDTO toDTO(InterestRequest i) {
        InterestRequestDTO dto = new InterestRequestDTO();
        dto.setId(i.getId());
        dto.setStatus(i.getStatus());
        dto.setLocked(i.getLocked());
        dto.setLockedAt(i.getLockedAt());
        dto.setRejectionMessage(i.getRejectionMessage());
        dto.setFullName(i.getFullName());
        dto.setEmail(i.getEmail());
        dto.setTelephone(i.getTelephone());
        dto.setMessage(i.getMessage());
        dto.setProposedBudget(i.getProposedBudget());
        dto.setCreatedAt(i.getCreatedAt());
        dto.setUpdatedAt(i.getUpdatedAt());

        // Rental contract fields
        dto.setRentalStartDate(i.getRentalStartDate());
        dto.setRentalEndDate(i.getRentalEndDate());
        dto.setRentalDurationMonths(i.getRentalDurationMonths());
        dto.setRentalAmount(i.getRentalAmount());
        dto.setRentalNotes(i.getRentalNotes());

        Property p = i.getProperty();
        if (p != null) {
            dto.setPropertyId(p.getId());
            dto.setPropertyTitle(p.getTitre());
            dto.setPropertyCity(p.getCity());
            dto.setPropertyCountry(p.getCountry());
            dto.setPropertyCategory(p.getCategory());
            if (p.getMainImageId() != null) {
                dto.setPropertyMainImageUrl("/api/images/public/" + p.getMainImageId());
            }
            if (p.getAgencyAdmin() != null) {
                dto.setAgencyName(p.getAgencyAdmin().getFullName());
            }
        }
        return dto;
    }
}
