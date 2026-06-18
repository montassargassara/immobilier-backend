package com.immobilier.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for turning a stored {@code User.profileImagePath}
 * into an absolute, JWT-protected avatar URL.
 *
 * The path itself is the ONLY persisted field. Every DTO that exposes an
 * avatar (AuthDTO, UserDTO, UserProfileDTO) must build its URL here so the
 * format never diverges.
 */
@Component
public class AvatarUrlBuilder {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /** @return absolute avatar URL, or {@code null} when the user has no photo. */
    public String build(String profileImagePath) {
        if (profileImagePath == null || profileImagePath.isBlank()) {
            return null;
        }
        return baseUrl + "/api/profile/avatar/" + profileImagePath;
    }
}
