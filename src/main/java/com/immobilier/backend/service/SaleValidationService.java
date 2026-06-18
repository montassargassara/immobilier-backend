package com.immobilier.backend.service;

import com.immobilier.backend.dto.DirectSaleRequest;
import com.immobilier.backend.dto.SaleValidationRequestDTO;
import com.immobilier.backend.entity.*;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.PendingSaleApprovalStatus;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SaleValidationService {

    private final SaleValidationRequestRepository validationRepo;
    private final PropertyRepository              propertyRepository;
    private final UserRepository                  userRepository;
    private final InterestRequestRepository       interestRequestRepository;
    private final NotificationService             notificationService;
    private final PropertyOwnershipService        ownershipService;
    private final CommissionService               commissionService;
    // @Lazy breaks the PropertyService ↔ SaleValidationService circular dependency
    private final PropertyService                 propertyService;

    @Value("${app.base-url:http://localhost:8080}")
    private String apiBase;

    public SaleValidationService(
            SaleValidationRequestRepository validationRepo,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            InterestRequestRepository interestRequestRepository,
            NotificationService notificationService,
            PropertyOwnershipService ownershipService,
            CommissionService commissionService,
            @Lazy PropertyService propertyService) {
        this.validationRepo            = validationRepo;
        this.propertyRepository        = propertyRepository;
        this.userRepository            = userRepository;
        this.interestRequestRepository = interestRequestRepository;
        this.notificationService       = notificationService;
        this.ownershipService          = ownershipService;
        this.commissionService         = commissionService;
        this.propertyService           = propertyService;
    }

    // ─── Create a validation request (direct-sale path) ──────────────────────

    @Transactional
    public SaleValidationRequestDTO createForDirectSale(Property property,
                                                        DirectSaleRequest req,
                                                        User requester) {
        // Guard: reject if property already has a pending validation
        validationRepo.findByPropertyIdAndStatus(property.getId(), PendingSaleApprovalStatus.PENDING)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Ce bien a déjà une demande de validation en attente (ID " + existing.getId() + ").");
                });

        SaleValidationRequest svr = new SaleValidationRequest();
        svr.setProperty(property);
        svr.setRequester(requester);
        svr.setTargetStatus(req.getTargetStatus());
        svr.setSource("DIRECT_SALE");

        // Resolve buyer reference if existing client
        Long existingBuyerId = req.getExistingClientId();
        if (existingBuyerId != null) {
            userRepository.findById(existingBuyerId).ifPresent(svr::setBuyer);
        }
        svr.setClientNom(req.getClientNom());
        svr.setClientPrenom(req.getClientPrenom());
        svr.setClientEmail(req.getClientEmail());
        svr.setClientTelephone(req.getClientTelephone());

        // Rental fields
        svr.setRentalStartDate(req.getRentalStartDate());
        svr.setRentalDurationMonths(req.getRentalDurationMonths());
        svr.setRentalAmount(req.getRentalAmount());
        svr.setRentalNotes(req.getRentalNotes());

        // Put property on hold
        property.setStatut("EN_ATTENTE");
        propertyRepository.save(property);

        SaleValidationRequest saved = validationRepo.save(svr);
        notifyOwner(saved, requester);

        log.info("Sale validation request #{} created — property {} → {} (requester {})",
                saved.getId(), property.getId(), req.getTargetStatus(), requester.getEmail());

        return SaleValidationRequestDTO.from(saved, apiBase);
    }

    @Transactional
    public SaleValidationRequestDTO createForCrmLead(Property property,
                                                     InterestRequest ir,
                                                     String targetStatus,
                                                     String rentalStartDate,
                                                     Integer rentalDurationMonths,
                                                     Double rentalAmount,
                                                     String rentalNotes,
                                                     User requester) {
        validationRepo.findByPropertyIdAndStatus(property.getId(), PendingSaleApprovalStatus.PENDING)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Ce bien a déjà une demande de validation en attente (ID " + existing.getId() + ").");
                });

        SaleValidationRequest svr = new SaleValidationRequest();
        svr.setProperty(property);
        svr.setRequester(requester);
        svr.setTargetStatus(targetStatus);
        svr.setSource("CRM_LEAD");
        svr.setInterestRequest(ir);

        // Buyer from the lead
        if (ir.getUser() != null) {
            svr.setBuyer(ir.getUser());
        }

        // Rental fields
        svr.setRentalStartDate(rentalStartDate);
        svr.setRentalDurationMonths(rentalDurationMonths);
        svr.setRentalAmount(rentalAmount);
        svr.setRentalNotes(rentalNotes);

        // Put property on hold
        property.setStatut("EN_ATTENTE");
        propertyRepository.save(property);

        SaleValidationRequest saved = validationRepo.save(svr);
        notifyOwner(saved, requester);

        log.info("Sale validation request #{} created (CRM lead #{}) — property {} → {}",
                saved.getId(), ir.getId(), property.getId(), targetStatus);

        return SaleValidationRequestDTO.from(saved, apiBase);
    }

    // ─── Approve ─────────────────────────────────────────────────────────────

    @Transactional
    public SaleValidationRequestDTO approve(Long id, User reviewer,
                                            com.immobilier.backend.dto.ApproveValidationRequest body) {
        SaleValidationRequest svr = findAndAuthorize(id, reviewer);

        String targetStatus = svr.getTargetStatus();
        Property property   = svr.getProperty();

        // ── Mandatory admin-entered terms — NO automatic default ────────────
        Double finalPrice    = body != null ? body.getFinalPrice() : null;
        Double commissionPct = body != null ? body.getCommissionPercentage() : null;
        if (finalPrice == null || finalPrice <= 0) {
            throw new IllegalArgumentException(
                    "Le prix final est obligatoire pour valider la transaction.");
        }
        if (commissionPct == null || commissionPct < 0) {
            throw new IllegalArgumentException(
                    "Le pourcentage de commission est obligatoire pour valider la transaction.");
        }

        // Apply the negotiated final price before completion so all downstream
        // logic (commission base, dashboards) uses the real validated amount.
        if ("VENDU".equals(targetStatus)) {
            property.setPrixVente(finalPrice);
        } else {
            property.setPrixLocation(finalPrice);
        }
        svr.setFinalPrice(finalPrice);
        svr.setCommissionPercentage(commissionPct);

        if ("DIRECT_SALE".equals(svr.getSource())) {
            // Reconstruct the DirectSaleRequest and delegate to PropertyService
            DirectSaleRequest req = new DirectSaleRequest();
            req.setTargetStatus(targetStatus);
            if (svr.getBuyer() != null) {
                req.setExistingClientId(svr.getBuyer().getId());
            } else {
                req.setClientNom(svr.getClientNom());
                req.setClientPrenom(svr.getClientPrenom());
                req.setClientEmail(svr.getClientEmail());
                req.setClientTelephone(svr.getClientTelephone());
            }
            req.setRentalStartDate(svr.getRentalStartDate());
            req.setRentalDurationMonths(svr.getRentalDurationMonths());
            req.setRentalAmount(svr.getRentalAmount());
            req.setRentalNotes(svr.getRentalNotes());

            propertyService.completeValidatedDirectSale(property, req, reviewer);

        } else {
            // CRM_LEAD path — complete the lead conversion inline
            completeLeadConversion(svr, property, targetStatus, reviewer);
        }

        // Record staff / agency commissions for the requester who brokered the
        // deal. The STAFF rate is the admin-entered % (no User.commissionRate
        // fallback, no 4% default) so it is always created on approval.
        commissionService.recordForCompletedSale(
                property, svr.getRequester(),
                "VENDU".equals(targetStatus) ? "SALE" : "RENT",
                commissionPct);

        svr.setStatus(PendingSaleApprovalStatus.APPROVED);
        svr.setReviewedBy(reviewer);
        svr.setReviewedAt(LocalDateTime.now());
        validationRepo.save(svr);

        // Notify the requester
        String label = "VENDU".equals(targetStatus) ? "vente" : "location";
        notificationService.create(svr.getRequester(),
                NotificationType.SALE_APPROVAL_GRANTED,
                "Validation accordée",
                String.format("Votre demande de %s pour « %s » a été acceptée par %s.",
                        label, property.getTitre(), reviewer.getFullName()),
                property.getId());

        log.info("Sale validation #{} approved by {}", id, reviewer.getEmail());
        return SaleValidationRequestDTO.from(svr, apiBase);
    }

    // ─── Reject ──────────────────────────────────────────────────────────────

    @Transactional
    public SaleValidationRequestDTO reject(Long id, User reviewer, String reason) {
        SaleValidationRequest svr = findAndAuthorize(id, reviewer);

        Property property = svr.getProperty();

        // Revert property to DISPONIBLE
        property.setStatut("DISPONIBLE");
        propertyRepository.save(property);

        svr.setStatus(PendingSaleApprovalStatus.REJECTED);
        svr.setRejectionReason(reason);
        svr.setReviewedBy(reviewer);
        svr.setReviewedAt(LocalDateTime.now());
        validationRepo.save(svr);

        // Notify the requester
        String label = "VENDU".equals(svr.getTargetStatus()) ? "vente" : "location";
        String reasonPart = (reason != null && !reason.isBlank()) ? " Motif : " + reason : "";
        notificationService.create(svr.getRequester(),
                NotificationType.SALE_APPROVAL_REJECTED,
                "Validation refusée",
                String.format("Votre demande de %s pour « %s » a été refusée par %s.%s",
                        label, property.getTitre(), reviewer.getFullName(), reasonPart),
                property.getId());

        log.info("Sale validation #{} rejected by {} — reason: {}", id, reviewer.getEmail(), reason);
        return SaleValidationRequestDTO.from(svr, apiBase);
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public List<SaleValidationRequestDTO> getPendingForOwner(User owner) {
        List<SaleValidationRequest> list;
        if (owner.getRole() == RoleType.SUPER_ADMIN) {
            list = validationRepo.findPendingForSuperAdmin();
        } else {
            Long adminId = resolveAdminId(owner);
            list = adminId != null
                    ? validationRepo.findPendingForAgencyAdmin(adminId)
                    : List.of();
        }
        return list.stream()
                .map(r -> SaleValidationRequestDTO.from(r, apiBase))
                .collect(Collectors.toList());
    }

    public List<SaleValidationRequestDTO> getMyRequests(User requester) {
        return validationRepo.findByRequesterIdOrderByCreatedAtDesc(requester.getId())
                .stream()
                .map(r -> SaleValidationRequestDTO.from(r, apiBase))
                .collect(Collectors.toList());
    }

    public long countPendingForOwner(User owner) {
        if (owner.getRole() == RoleType.SUPER_ADMIN) {
            return validationRepo.countPendingForSuperAdmin();
        }
        Long adminId = resolveAdminId(owner);
        if (adminId == null) return 0L;
        return validationRepo.countPendingForAgencyAdmin(adminId);
    }

    // ─── CRM lead completion (called from approve()) ──────────────────────────

    private void completeLeadConversion(SaleValidationRequest svr,
                                        Property property,
                                        String targetStatus,
                                        User reviewer) {
        InterestRequest ir = svr.getInterestRequest();
        if (ir == null) {
            throw new IllegalStateException("Aucun lead CRM lié à cette demande de validation.");
        }

        if ("VENDU".equals(targetStatus)) {
            property.setStatut("VENDU");
            property.setIsFinalized(true);
            propertyRepository.save(property);

        } else {
            // LOUE — apply rental contract on both the property and the lead
            if (svr.getRentalStartDate() == null || svr.getRentalDurationMonths() == null
                    || svr.getRentalDurationMonths() < 1) {
                throw new IllegalArgumentException(
                        "La date de début et la durée sont obligatoires pour une location.");
            }
            LocalDate start = LocalDate.parse(svr.getRentalStartDate());
            LocalDate end   = start.plusMonths(svr.getRentalDurationMonths());

            ir.setRentalStartDate(start);
            ir.setRentalEndDate(end);
            ir.setRentalDurationMonths(svr.getRentalDurationMonths());
            ir.setRentalAmount(svr.getRentalAmount());
            ir.setRentalNotes(svr.getRentalNotes());

            property.setStatut("LOUE");
            property.setRentalStartDate(start.atStartOfDay());
            property.setRentalEndDate(end.atStartOfDay());
            property.setRentalDurationMonths(svr.getRentalDurationMonths());
            propertyRepository.save(property);
        }

        // Lock the lead
        ir.setStatus("CONVERTI_" + (targetStatus.equals("VENDU") ? "VENTE" : "LOCATION"));
        ir.setLocked(true);
        ir.setLockedAt(LocalDateTime.now());
        interestRequestRepository.save(ir);

        // Auto-refuse sibling leads
        autoRefuseSiblings(ir, property);

        log.info("CRM lead #{} locked as {} after validation approval", ir.getId(), targetStatus);
    }

    private void autoRefuseSiblings(InterestRequest winner, Property property) {
        interestRequestRepository.findActiveLeadsForProperty(property.getId(), winner.getId())
                .forEach(sib -> {
                    sib.setStatus("REFUSE");
                    sib.setLocked(true);
                    sib.setLockedAt(LocalDateTime.now());
                    sib.setRejectionMessage("Un autre client a été retenu pour ce bien.");
                    interestRequestRepository.save(sib);
                    if (sib.getUser() != null) {
                        notificationService.create(sib.getUser(),
                                NotificationType.LEAD_AUTO_REFUSED,
                                "Bien non disponible",
                                String.format("Le bien « %s » n'est plus disponible : un autre acheteur a été retenu.",
                                        property.getTitre()),
                                sib.getId());
                    }
                });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Delegates to {@link PropertyOwnershipService} — kept for callers outside this class. */
    public boolean isPropertyOwner(User user, Property property) {
        return ownershipService.isOwner(user, property);
    }

    private SaleValidationRequest findAndAuthorize(Long id, User reviewer) {
        SaleValidationRequest svr = validationRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande de validation introuvable: " + id));

        if (svr.getStatus() != PendingSaleApprovalStatus.PENDING) {
            throw new IllegalStateException("Cette demande a déjà été traitée.");
        }

        if (!ownershipService.isOwner(reviewer, svr.getProperty())) {
            throw new SecurityException("Vous n'êtes pas propriétaire de ce bien.");
        }
        return svr;
    }

    private void notifyOwner(SaleValidationRequest svr, User requester) {
        User owner = resolvePropertyOwner(svr.getProperty());
        if (owner == null) return;

        String label = "VENDU".equals(svr.getTargetStatus()) ? "vente" : "location";
        String buyerDisplay = svr.getBuyer() != null
                ? svr.getBuyer().getFullName()
                : ((svr.getClientPrenom() != null ? svr.getClientPrenom() + " " : "") +
                   (svr.getClientNom() != null ? svr.getClientNom() : "")).trim();
        if (buyerDisplay.isBlank()) buyerDisplay = "un client";

        notificationService.create(owner,
                NotificationType.SALE_APPROVAL_REQUESTED,
                "Validation de " + label + " requise",
                String.format("%s souhaite réaliser la %s de votre bien « %s » au profit de %s. " +
                              "Veuillez accepter ou refuser cette demande.",
                        requester.getFullName(),
                        label,
                        svr.getProperty().getTitre(),
                        buyerDisplay),
                svr.getId());
    }

    private User resolvePropertyOwner(Property property) {
        if ("SUPER_ADMIN_OWNED".equals(property.getOwnerType())) {
            return userRepository.findByRole(RoleType.SUPER_ADMIN).stream().findFirst().orElse(null);
        }
        return property.getAgencyAdmin();
    }

    private Long resolveAdminId(User user) {
        if (user.getRole() == RoleType.ADMIN) return user.getId();
        return userRepository.findTopAdminAncestor(user.getId()).map(User::getId).orElse(null);
    }
}
