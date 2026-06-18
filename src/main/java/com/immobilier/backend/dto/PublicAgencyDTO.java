package com.immobilier.backend.dto;

import lombok.Data;

/**
 * Minimal public-facing agency info included in every public property response.
 * Never exposes internal commission rates, affiliate flags, or share-request state.
 */
@Data
public class PublicAgencyDTO {
    private Long id;
    /** Display name shown to visitors. */
    private String name;
    /**
     * "SUPER_ADMIN" for platform-owned properties, "AGENCY" for partner agencies.
     * Drives the badge style on the frontend.
     */
    private String type;
    private String phone;
    private String email;
    /** Pre-built WhatsApp deep-link: https://wa.me/{digits} — null when no phone. */
    private String whatsappLink;
    /** Physical/city address shown in the contact card. */
    private String address;
    /** Public URL of the agency's profile/logo image — null when not set. */
    private String logoUrl;
}
