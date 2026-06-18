package com.immobilier.backend.service;

import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.PropertyRepository;
import com.immobilier.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Centralises all property visibility / access-control logic.
 *
 * Visibility matrix:
 *  SUPER_ADMIN → all properties
 *  ADMIN       → agency-owned (owner = self) + shared SUPER_ADMIN properties
 *  RC/COMMERCIAL → same as their ADMIN ancestor
 *  Others      → no access to the admin property module
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyVisibilityService {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    /**
     * Returns the ADMIN ancestor for a given user.
     * Returns the user itself if they are ADMIN, null if SUPER_ADMIN.
     */
    public Optional<User> resolveAgencyAdmin(User user) {
        if (user.getRole() == RoleType.SUPER_ADMIN) {
            return Optional.empty(); // super admin has no agency
        }
        if (user.getRole() == RoleType.ADMIN) {
            return Optional.of(user);
        }
        // For RC / COMMERCIAL / others — climb the hierarchy
        return userRepository.findTopAdminAncestor(user.getId());
    }

    /**
     * Returns the full list of properties visible to the given user.
     *
     * Role scope:
     *   SUPER_ADMIN → everything
     *   ADMIN / RESPONSABLE_COMMERCIAL → all properties in their agency
     *                                   (own + accepted shares, including pending validations)
     *   COMMERCIAL → only properties they personally created (regardless of validation state)
     */
    public List<Property> getVisibleProperties(User currentUser) {
        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            return propertyRepository.findByIsActiveTrue();
        }

        Optional<User> agencyAdminOpt = resolveAgencyAdmin(currentUser);
        if (agencyAdminOpt.isEmpty()) {
            log.warn("User {} has no agency admin ancestor — returning empty list", currentUser.getId());
            return List.of();
        }

        // COMMERCIAL sees all agency properties (same as ADMIN/RESPONSABLE).
        // Edit restrictions are enforced per-operation in PropertyService, not at the list level.
        return propertyRepository.findVisiblePropertiesForAgency(agencyAdminOpt.get().getId());
    }

    /**
     * Returns true if the given user can read/write this property.
     * Never trusts the caller — re-checks in the database.
     */
    public boolean canAccess(User currentUser, Property property) {
        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            return true;
        }

        Optional<User> agencyAdminOpt = resolveAgencyAdmin(currentUser);
        if (agencyAdminOpt.isEmpty()) {
            return false;
        }

        Long agencyAdminId = agencyAdminOpt.get().getId();
        return propertyRepository.isPropertyVisibleForAgency(property.getId(), agencyAdminId);
    }

    /**
     * Determines the ownerType string for a newly created property.
     */
    public String resolveOwnerType(User currentUser) {
        return (currentUser.getRole() == RoleType.SUPER_ADMIN)
                ? "SUPER_ADMIN_OWNED"
                : "AGENCY_OWNED";
    }

    /**
     * Determines the agencyAdmin to attach to a new property.
     * Returns null for SUPER_ADMIN-created properties.
     */
    public User resolvePropertyOwner(User currentUser) {
        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            return null;
        }
        return resolveAgencyAdmin(currentUser).orElse(null);
    }
}
