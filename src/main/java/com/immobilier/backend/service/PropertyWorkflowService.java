package com.immobilier.backend.service;

import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.PendingSaleApprovalStatus;
import com.immobilier.backend.enums.PropertyValidationStatus;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.PropertyRepository;
import com.immobilier.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Centralises the role-based property validation state machine and the
 * notification routing that goes with each transition.
 *
 *   COMMERCIAL creates              → PENDING_RESPONSABLE   → notify RESPONSABLE
 *   RESPONSABLE confirms            → PENDING_ADMIN         → notify ADMIN
 *   ADMIN sets commission, approves → APPROVED              → notify creator
 *   ADMIN/SUPER_ADMIN creates       → APPROVED              (skip workflow)
 *   Validator rejects               → REJECTED              → notify creator
 *
 * Sale notifications:
 *   COMMERCIAL sells   → notify ADMIN (skips RESPONSABLE per spec)
 *   RESPONSABLE sells  → notify ADMIN
 *   AGENCY sells SUPER_ADMIN_OWNED → notify SUPER_ADMIN
 *   Missing commission on sold property → COMMISSION_REQUIRED to ADMIN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyWorkflowService {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyVisibilityService visibilityService;

    // ─── Initial state on create ─────────────────────────────────────────────

    /**
     * Decide the initial validation state for a freshly-created property.
     * ADMIN/SUPER_ADMIN are validators themselves — their creations are APPROVED immediately.
     */
    public PropertyValidationStatus initialStatusFor(RoleType creatorRole) {
        if (creatorRole == null) return PropertyValidationStatus.APPROVED;
        switch (creatorRole) {
            case COMMERCIAL:            return PropertyValidationStatus.PENDING_RESPONSABLE;
            case RESPONSABLE_COMMERCIAL: return PropertyValidationStatus.PENDING_ADMIN;
            case ADMIN:
            case SUPER_ADMIN:
            default:
                return PropertyValidationStatus.APPROVED;
        }
    }

    // ─── Notifications on submission ─────────────────────────────────────────

    @Transactional
    public void notifySubmission(Property property, User author) {
        if (property.getValidationStatus() == PropertyValidationStatus.APPROVED) {
            return; // nothing to validate
        }
        List<User> recipients = resolveValidatorsFor(property.getValidationStatus(), author);
        String title = "Nouveau bien à valider";
        String message = String.format(
                "%s (%s) a créé le bien \"%s\". En attente de votre validation.",
                author.getFullName(), author.getRole(), property.getTitre());
        for (User r : recipients) {
            notificationService.create(r, NotificationType.PROPERTY_PENDING_VALIDATION,
                    title, message, property.getId());
        }
    }

    // ─── Validation transitions ──────────────────────────────────────────────

    /**
     * RESPONSABLE confirms a COMMERCIAL submission and forwards it to ADMIN.
     */
    @Transactional
    public Property validateByResponsable(Property property, User responsable) {
        if (responsable.getRole() != RoleType.RESPONSABLE_COMMERCIAL
                && responsable.getRole() != RoleType.ADMIN
                && responsable.getRole() != RoleType.SUPER_ADMIN) {
            throw new RuntimeException("Seul un Responsable Commercial ou supérieur peut valider à cette étape");
        }
        if (property.getValidationStatus() != PropertyValidationStatus.PENDING_RESPONSABLE) {
            throw new RuntimeException("Cette propriété n'est pas en attente de validation par le Responsable");
        }
        property.setPriceLocked(true);
        property.setValidationStatus(PropertyValidationStatus.PENDING_ADMIN);
        Property saved = propertyRepository.save(property);
        // Notify the ADMIN of the agency
        notifyValidators(saved, PropertyValidationStatus.PENDING_ADMIN,
                "Bien à valider et tarifer",
                String.format("Le bien \"%s\" attend votre validation finale et la définition de la commission.",
                        saved.getTitre()));
        return saved;
    }

    /**
     * ADMIN approves the property and locks the commission. Commission must already be set
     * via the commission endpoint, or supplied here in the same call (handled by the controller).
     */
    @Transactional
    public Property validateByAdmin(Property property, User admin) {
        if (admin.getRole() != RoleType.ADMIN && admin.getRole() != RoleType.SUPER_ADMIN) {
            throw new RuntimeException("Seul un Admin ou Super Admin peut approuver un bien");
        }
        if (property.getValidationStatus() == PropertyValidationStatus.APPROVED) {
            return property; // already approved — idempotent
        }
        if (property.getPriceLocked() == null || !property.getPriceLocked()) {
            property.setPriceLocked(true);
        }
        property.setValidationStatus(PropertyValidationStatus.APPROVED);
        property.setRejectionReason(null);

        // Final approval publishes the listing: a property that was held
        // EN_ATTENTE only for the creation-validation workflow becomes
        // DISPONIBLE (sellable). Never override a terminal/locked state.
        if ("EN_ATTENTE".equals(property.getStatut())
                && property.getPendingSaleApproval() == null
                && !Boolean.TRUE.equals(property.getIsReservedByAffiliate())
                && !Boolean.TRUE.equals(property.getIsFinalized())) {
            property.setStatut("DISPONIBLE");
        }

        // NOTE: commission is NOT locked by validation. ADMIN/SUPER_ADMIN can
        // still adjust it afterwards; it only becomes immutable once the
        // property is actually sold/rented (handled by the sale-state guard).
        Property saved = propertyRepository.save(property);
        notifyAuthor(saved, NotificationType.PROPERTY_VALIDATED,
                "Bien approuvé",
                String.format("Votre bien \"%s\" a été approuvé et est désormais actif.", saved.getTitre()));
        return saved;
    }

    @Transactional
    public Property reject(Property property, User validator, String reason) {
        if (validator.getRole() != RoleType.RESPONSABLE_COMMERCIAL
                && validator.getRole() != RoleType.ADMIN
                && validator.getRole() != RoleType.SUPER_ADMIN) {
            throw new RuntimeException("Vous n'êtes pas autorisé à refuser un bien");
        }
        if (property.getValidationStatus() == PropertyValidationStatus.APPROVED) {
            throw new RuntimeException("Un bien déjà approuvé ne peut pas être refusé. Désactivez-le ou modifiez-le.");
        }
        property.setValidationStatus(PropertyValidationStatus.REJECTED);
        property.setRejectionReason(reason);
        Property saved = propertyRepository.save(property);
        notifyAuthor(saved, NotificationType.PROPERTY_REJECTED,
                "Bien refusé",
                String.format("Votre bien \"%s\" a été refusé. Raison: %s",
                        saved.getTitre(),
                        reason != null && !reason.isBlank() ? reason : "Non précisée"));
        return saved;
    }

    // ─── Modification audit trail ────────────────────────────────────────────

    /**
     * Fire-and-forget audit notification when a COMMERCIAL modifies their own property.
     * No approval gate — pure trace per spec.
     */
    @Transactional
    public void notifyModification(Property property, User editor) {
        if (editor.getRole() != RoleType.COMMERCIAL) return;
        Optional<User> admin = visibilityService.resolveAgencyAdmin(editor);
        Optional<User> responsable = userRepository.findByParentId(admin.map(User::getId).orElse(-1L)).stream()
                .filter(u -> u.getRole() == RoleType.RESPONSABLE_COMMERCIAL)
                .findFirst();
        String title = "Bien modifié par un commercial";
        String message = String.format("%s a modifié le bien \"%s\".",
                editor.getFullName(), property.getTitre());
        admin.ifPresent(a -> notificationService.create(a, NotificationType.PROPERTY_MODIFIED,
                title, message, property.getId()));
        responsable.ifPresent(r -> notificationService.create(r, NotificationType.PROPERTY_MODIFIED,
                title, message, property.getId()));
    }

    // ─── Sale notifications ──────────────────────────────────────────────────

    /**
     * Called when a property is flipped to VENDU/LOUE outside the affiliate flow.
     * Routes upward notifications and flags the missing-commission case.
     */
    @Transactional
    public void notifySale(Property property, User seller, String newStatut) {
        // 1) Notify ADMIN (or skip if seller is ADMIN/SUPER_ADMIN)
        Optional<User> agencyAdmin = visibilityService.resolveAgencyAdmin(seller);
        if (seller.getRole() == RoleType.COMMERCIAL || seller.getRole() == RoleType.RESPONSABLE_COMMERCIAL) {
            agencyAdmin.ifPresent(admin -> notificationService.create(
                    admin,
                    NotificationType.PROPERTY_SOLD_BY_AGENCY,
                    "Vente/location enregistrée",
                    String.format("%s a marqué \"%s\" comme %s. Prix: %s.",
                            seller.getFullName(),
                            property.getTitre(),
                            newStatut,
                            formatPrice(property)),
                    property.getId()));

            // Missing commission → ADMIN must assign one
            if (property.getCommissionPercentage() == null || property.getCommissionPercentage() <= 0) {
                agencyAdmin.ifPresent(admin -> notificationService.create(
                        admin,
                        NotificationType.COMMISSION_REQUIRED,
                        "Commission manquante",
                        String.format("Le bien \"%s\" a été vendu sans commission définie. Veuillez en assigner une.",
                                property.getTitre()),
                        property.getId()));
            }
        }

        // 2) Notify SUPER_ADMIN when an agency sells a SUPER_ADMIN_OWNED (shared) property
        if ("SUPER_ADMIN_OWNED".equals(property.getOwnerType())) {
            for (User sa : userRepository.findByRoleAndIsActiveTrue(RoleType.SUPER_ADMIN)) {
                notificationService.create(
                        sa,
                        NotificationType.PROPERTY_SOLD_BY_AGENCY,
                        "Vente d'un bien Super Admin par une agence",
                        String.format("%s a marqué votre bien partagé \"%s\" comme %s. Prix: %s, commission: %s.",
                                seller.getFullName(),
                                property.getTitre(),
                                newStatut,
                                formatPrice(property),
                                formatCommission(property)),
                        property.getId());
            }
        }
    }

    // ─── Sale approval workflow ──────────────────────────────────────────────

    /**
     * Determines whether this status change must go through the approval workflow
     * rather than being applied immediately.
     *
     * Only VENDU, LOUE and EN_ATTENTE are "significant" statuses that require approval
     * when the requester does not own the property. DISPONIBLE is a neutral reset
     * that is always applied directly.
     *
     * Approval rules:
     *  - SUPER_ADMIN on SUPER_ADMIN_OWNED (own)  → immediate
     *  - ADMIN on AGENCY_OWNED (own agency)       → immediate
     *  - SUPER_ADMIN on AGENCY_OWNED              → ADMIN of that agency must approve
     *  - ADMIN on SUPER_ADMIN_OWNED               → SUPER_ADMIN must approve
     *  - COMMERCIAL / RESPONSABLE on any property → ADMIN must approve (may escalate)
     */
    public boolean requiresSaleApproval(Property property, User seller, String newStatus) {
        RoleType role = seller.getRole();

        // COMMERCIAL and RESPONSABLE_COMMERCIAL never apply status changes directly —
        // ALL statuses (including DISPONIBLE) must go through ADMIN approval.
        if (role == RoleType.COMMERCIAL || role == RoleType.RESPONSABLE_COMMERCIAL) return true;

        // For SUPER_ADMIN and ADMIN, only significant statuses need cross-ownership approval.
        if (!isSignificantStatus(newStatus)) return false;

        String ownerType = property.getOwnerType();

        // SUPER_ADMIN on own property (SUPER_ADMIN_OWNED or legacy null) → immediate
        if (role == RoleType.SUPER_ADMIN && !"AGENCY_OWNED".equals(ownerType)) return false;

        // ADMIN on their own agency's property (AGENCY_OWNED or legacy) → immediate
        if (role == RoleType.ADMIN && !"SUPER_ADMIN_OWNED".equals(ownerType)) return false;

        // Remaining cases: SUPER_ADMIN→AGENCY_OWNED, ADMIN→SUPER_ADMIN_OWNED → require approval
        return true;
    }

    private boolean isSignificantStatus(String status) {
        return "VENDU".equals(status) || "LOUE".equals(status) || "EN_ATTENTE".equals(status);
    }

    /**
     * Creates a pending sale request at the correct level in the approval chain.
     *
     * Chain for SUPER_ADMIN_OWNED:
     *   COMMERCIAL/RESPONSABLE → (level 1) ADMIN approves → (level 2) SUPER_ADMIN approves → applied
     *
     * Chain for AGENCY_OWNED:
     *   COMMERCIAL/RESPONSABLE → (level 1) ADMIN approves → applied (no SUPER_ADMIN step)
     *
     * Chain for ADMIN selling SUPER_ADMIN_OWNED:
     *   ADMIN → (level 1) SUPER_ADMIN approves → applied
     */
    @Transactional
    public void requestSaleApproval(Property property, User requester, String desiredStatut) {
        RoleType nextApproverRole = resolveFirstApproverRole(property, requester);
        
        // Sauvegarder la demande en attente
        property.setPendingSaleApproval(PendingSaleApprovalStatus.PENDING);
        property.setPendingSaleStatut(desiredStatut);
        property.setPendingSaleRejectionReason(null);
        property.setPendingSaleRequestedBy(requester);
        property.setPendingSaleApproverRole(nextApproverRole);
        propertyRepository.save(property);
        
        // Notifier le(s) approbateur(s)
        List<User> approvers = resolveApproversByRole(property, requester, nextApproverRole);
        String title = "Demande de vente en attente";
        String message = String.format(
                "%s demande de marquer \"%s\" comme %s.",
                requester.getFullName(), property.getTitre(), desiredStatut);
        
        for (User approver : approvers) {
            notificationService.create(approver, NotificationType.SALE_APPROVAL_REQUESTED,
                    title, message, property.getId());
        }
    }

    /**
     * Processes an approval by the current approver.
     *
     * Two-level escalation for SUPER_ADMIN_OWNED (COMMERCIAL/RESPONSABLE request):
     *  - ADMIN approves → escalate to SUPER_ADMIN (level 2)
     *  - SUPER_ADMIN approves → apply the status
     *
     * Single-level for AGENCY_OWNED (SUPER_ADMIN request):
     *  - ADMIN of that agency approves → apply directly (SUPER_ADMIN already initiated it)
     *
     * Single-level for SUPER_ADMIN_OWNED (ADMIN request):
     *  - SUPER_ADMIN approves → apply directly
     */
    @Transactional
    public void approveSaleRequest(Property property, User approver) {
        RoleType approverRole = approver.getRole();
        boolean isSuperAdminOwned = "SUPER_ADMIN_OWNED".equals(property.getOwnerType());
        User originalRequester = property.getPendingSaleRequestedBy();

        // ADMIN approving a COMMERCIAL/RESPONSABLE request on a SUPER_ADMIN_OWNED property
        // → escalate the request to SUPER_ADMIN (do not apply the status yet)
        if (approverRole == RoleType.ADMIN && isSuperAdminOwned
                && originalRequester != null
                && (originalRequester.getRole() == RoleType.COMMERCIAL
                    || originalRequester.getRole() == RoleType.RESPONSABLE_COMMERCIAL)) {

            property.setPendingSaleApproval(PendingSaleApprovalStatus.PENDING);
            property.setPendingSaleRequestedBy(approver);      // ADMIN is now the requester toward SUPER_ADMIN
            property.setPendingSaleApproverRole(RoleType.SUPER_ADMIN);
            propertyRepository.save(property);

            // Inform original requester (COMMERCIAL/RESPONSABLE) that it was forwarded
            notificationService.create(originalRequester, NotificationType.SALE_APPROVAL_REQUESTED,
                    "Demande transmise au Super Admin",
                    String.format("Votre demande de vente pour \"%s\" a été acceptée par votre agence et transmise au Super Admin pour validation finale.",
                            property.getTitre()),
                    property.getId());

            // Notify all SUPER_ADMINs
            for (User sa : userRepository.findByRoleAndIsActiveTrue(RoleType.SUPER_ADMIN)) {
                notificationService.create(sa, NotificationType.SALE_APPROVAL_REQUESTED,
                        "Validation finale de vente requise",
                        String.format("L'agence %s a accepté une demande de vente de %s pour votre bien \"%s\" (%s). Veuillez approuver ou refuser.",
                                approver.getFullName(), originalRequester.getFullName(),
                                property.getTitre(), property.getPendingSaleStatut()),
                        property.getId());
            }
            return;
        }

        // All other cases: apply the status directly
        property.setPendingSaleApproval(PendingSaleApprovalStatus.APPROVED);
        property.setStatut(property.getPendingSaleStatut());
        propertyRepository.save(property);

        // Notify the requester (could be ADMIN if escalated, or original COMMERCIAL/RESPONSABLE)
        if (originalRequester != null) {
            notificationService.create(originalRequester, NotificationType.SALE_APPROVAL_GRANTED,
                    "Vente approuvée",
                    String.format("Votre demande de vente pour \"%s\" a été approuvée. Statut: %s.",
                            property.getTitre(), property.getPendingSaleStatut()),
                    property.getId());
        }
        notifySale(property, originalRequester != null ? originalRequester : approver,
                property.getPendingSaleStatut());
    }

    /**
     * Rejects the pending sale at the current level.
     * The property status stays unchanged and the requester is notified.
     */
    @Transactional
    public void rejectSaleRequest(Property property, User approver, String reason) {
        property.setPendingSaleApproval(PendingSaleApprovalStatus.REJECTED);
        property.setPendingSaleRejectionReason(reason);
        propertyRepository.save(property);

        User requester = property.getPendingSaleRequestedBy();
        if (requester != null) {
            notificationService.create(requester, NotificationType.SALE_APPROVAL_REJECTED,
                    "Demande de vente refusée",
                    String.format("Votre demande de vente pour \"%s\" a été refusée. Raison: %s.",
                            property.getTitre(),
                            reason != null && !reason.isBlank() ? reason : "Non précisée"),
                    property.getId());
        }
    }

    // ─── Approval chain helpers ──────────────────────────────────────────────

    private RoleType resolveFirstApproverRole(Property property, User requester) {
        RoleType role = requester.getRole();
        if (role == RoleType.COMMERCIAL || role == RoleType.RESPONSABLE_COMMERCIAL) {
            return RoleType.ADMIN; // First level is always ADMIN for staff roles
        }
        if (role == RoleType.SUPER_ADMIN) {
            // SUPER_ADMIN acting on AGENCY_OWNED → the agency's ADMIN must approve first
            return RoleType.ADMIN;
        }
        // ADMIN acting on SUPER_ADMIN_OWNED → SUPER_ADMIN must approve
        return RoleType.SUPER_ADMIN;
    }

    private List<User> resolveApproversByRole(Property property, User requester, RoleType targetRole) {
        if (targetRole == RoleType.SUPER_ADMIN) {
            return userRepository.findByRoleAndIsActiveTrue(RoleType.SUPER_ADMIN);
        }
        // Target is ADMIN — figure out which admin to notify
        if (requester.getRole() == RoleType.SUPER_ADMIN) {
            // SUPER_ADMIN on AGENCY_OWNED: notify the property's specific agency admin
            if (property.getAgencyAdmin() != null) {
                return List.of(property.getAgencyAdmin());
            }
            return List.of();
        }
        // COMMERCIAL / RESPONSABLE → resolve their own agency admin
        Optional<User> admin = visibilityService.resolveAgencyAdmin(requester);
        return admin.map(List::of).orElse(List.of());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private List<User> resolveValidatorsFor(PropertyValidationStatus status, User author) {
        Optional<User> agencyAdmin = visibilityService.resolveAgencyAdmin(author);
        if (status == PropertyValidationStatus.PENDING_RESPONSABLE) {
            // RESPONSABLE_COMMERCIAL within the same agency
            return agencyAdmin.map(admin -> userRepository.findByParentId(admin.getId()).stream()
                    .filter(u -> u.getRole() == RoleType.RESPONSABLE_COMMERCIAL && Boolean.TRUE.equals(u.getIsActive()))
                    .toList()
            ).orElse(List.of());
        }
        if (status == PropertyValidationStatus.PENDING_ADMIN) {
            return agencyAdmin.map(List::of).orElse(List.of());
        }
        return List.of();
    }

    private void notifyValidators(Property property, PropertyValidationStatus status,
                                  String title, String message) {
        User author = property.getCreatedBy();
        if (author == null) return;
        for (User v : resolveValidatorsFor(status, author)) {
            notificationService.create(v, NotificationType.PROPERTY_PENDING_VALIDATION,
                    title, message, property.getId());
        }
    }

    private void notifyAuthor(Property property, NotificationType type, String title, String message) {
        User author = property.getCreatedBy();
        if (author != null) {
            notificationService.create(author, type, title, message, property.getId());
        }
    }

    private String formatPrice(Property p) {
        Double price = p.getPrixVente() != null && p.getPrixVente() > 0 ? p.getPrixVente() : p.getPrixLocation();
        return price != null ? String.format("%,.0f TND", price) : "N/A";
    }

    private String formatCommission(Property p) {
        if (p.getCommissionPercentage() == null || p.getCommissionPercentage() == 0) return "Aucune";
        return p.getCommissionDisplay();
    }
}
