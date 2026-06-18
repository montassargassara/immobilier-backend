package com.immobilier.backend.service;

import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "does this user own this property?".
 * Kept in its own service to avoid circular dependencies between
 * PropertyService ↔ SaleValidationService ↔ InterestRequestService.
 */
@Service
@RequiredArgsConstructor
public class PropertyOwnershipService {

    /**
     * Returns {@code true} if {@code user} is the canonical owner of {@code property}
     * and therefore may approve/reject a cross-ownership sale validation.
     *
     * <ul>
     *   <li>COMMERCIAL / RESPONSABLE_COMMERCIAL → <b>never</b> an owner: every sale
     *       they initiate must be validated by an ADMIN / SUPER_ADMIN. They can
     *       never self-finalize a transaction, even on their own agency's property.</li>
     *   <li>SUPER_ADMIN_OWNED → only SUPER_ADMIN is the owner</li>
     *   <li>AGENCY_OWNED     → the property's {@code agencyAdmin} (ADMIN only)</li>
     *   <li>Legacy null      → ownerless; an ADMIN may finalize directly</li>
     * </ul>
     */
    public boolean isOwner(User user, Property property) {
        String ownerType = property.getOwnerType();
        RoleType role    = user.getRole();

        // Sales staff can NEVER finalize a deal themselves — always route through
        // the validation workflow so an ADMIN / SUPER_ADMIN approves it.
        if (role == RoleType.COMMERCIAL || role == RoleType.RESPONSABLE_COMMERCIAL) {
            return false;
        }

        if ("SUPER_ADMIN_OWNED".equals(ownerType)) {
            return role == RoleType.SUPER_ADMIN;
        }

        // AGENCY_OWNED or legacy null
        if (role == RoleType.SUPER_ADMIN) {
            return false; // super admin does not own an agency property
        }

        if (property.getAgencyAdmin() == null) {
            // No specific owner defined — an ADMIN may finalize directly
            return role == RoleType.ADMIN;
        }

        // role == ADMIN — owner only if this is their own agency
        return user.getId().equals(property.getAgencyAdmin().getId());
    }

    /**
     * Returns {@code true} if the requester does NOT own the property and therefore
     * a validation request must be created before the sale can proceed.
     */
    public boolean requiresValidation(User requester, Property property) {
        return !isOwner(requester, property);
    }
}
