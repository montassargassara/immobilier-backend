package com.immobilier.backend.enums;

/**
 * State machine for the role-based property validation workflow.
 *
 *   COMMERCIAL creates              → PENDING_RESPONSABLE
 *   RESPONSABLE confirms price      → PENDING_ADMIN
 *   ADMIN sets commission, approves → APPROVED
 *   ADMIN/SUPER_ADMIN creates       → APPROVED (skip workflow)
 *   Validator rejects               → REJECTED
 *
 * Only APPROVED properties are visible in agency listings, the public portal,
 * the affiliate workspace, and can be shared by SUPER_ADMIN.
 */
public enum PropertyValidationStatus {
    PENDING_RESPONSABLE,
    PENDING_ADMIN,
    APPROVED,
    REJECTED
}
