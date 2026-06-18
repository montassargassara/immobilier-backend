package com.immobilier.backend.enums;

public enum NotificationType {
    // ── Property sharing workflow ────────────────────────────────────────────
    SHARE_REQUEST_RECEIVED,   // agency receives a new share request
    SHARE_REQUEST_ACCEPTED,   // super admin notified agency accepted
    SHARE_REQUEST_REJECTED,   // super admin notified agency rejected
    SHARE_REQUEST_CANCELLED,  // agency notified super admin cancelled

    // ── Affiliate account lifecycle ──────────────────────────────────────────
    AFFILIATE_REGISTRATION,   // super admin notified of new pending affiliate
    AFFILIATE_APPROVED,       // affiliate notified their account was approved
    AFFILIATE_REJECTED,       // affiliate notified their account was rejected
    AFFILIATE_SUSPENDED,      // affiliate notified their account was suspended

    // ── Sale offer workflow ──────────────────────────────────────────────────
    SALE_OFFER_RECEIVED,      // agency/super-admin notified of a new offer
    SALE_OFFER_ACCEPTED,      // affiliate notified their offer was accepted
    SALE_OFFER_REJECTED,      // affiliate notified their offer was rejected
    SALE_OFFER_COMPLETED,     // affiliate notified sale was finalised

    // ── Monthly bonus ────────────────────────────────────────────────────────
    MONTHLY_BONUS_AWARDED,    // affiliate notified of a bonus for next month

    // ── Public client interest ───────────────────────────────────────────────
    PROPERTY_INTEREST_RECEIVED, // agency/super-admin notified a public client
                                // expressed interest in one of their properties

    // ── Property validation workflow (role-based) ────────────────────────────
    PROPERTY_PENDING_VALIDATION, // upstream validator notified of a new submission
    PROPERTY_VALIDATED,          // author notified their property was approved
    PROPERTY_REJECTED,           // author notified their property was rejected
    PROPERTY_MODIFIED,           // upstream roles notified of a modification (audit)
    COMMISSION_REQUIRED,         // ADMIN notified that a sold property has no commission
    PROPERTY_SOLD_BY_AGENCY,     // SUPER_ADMIN notified an agency sold/rented one of their properties

    // ── Sale approval workflow (pending sale requests) ───────────────────────
    SALE_APPROVAL_REQUESTED,    // approver notified of a pending sale request
    SALE_APPROVAL_GRANTED,      // requester notified their sale was approved
    SALE_APPROVAL_REJECTED,     // requester notified their sale was rejected

    // ── Agency self-registration workflow ────────────────────────────────────
    AGENCY_REGISTRATION,        // super admin notified of new pending agency registration
    AGENCY_APPROVED,            // agency admin notified their account was approved
    AGENCY_REJECTED,            // agency admin notified their account was rejected

    // ── Zone payment workflow ─────────────────────────────────────────────────
    ZONE_PAYMENT_SUBMITTED,     // super admin notified of a new zone payment proof
    ZONE_PAYMENT_APPROVED,      // affiliate notified their zone payment was approved
    ZONE_PAYMENT_REJECTED,      // affiliate notified their zone payment was rejected

    // ── CRM lead lifecycle ────────────────────────────────────────────────────
    LEAD_REFUSED,               // public client notified their interest was refused
    LEAD_CONVERTED_SALE,        // admin notified lead converted → property sold
    LEAD_CONVERTED_RENTAL,      // admin notified lead converted → property rented
    LEAD_AUTO_REFUSED,          // public client notified: another buyer was selected
    PROPERTY_AVAILABLE_AGAIN    // admin notified: rental expired, property is DISPONIBLE again
}
