package com.immobilier.backend.controller;

import com.immobilier.backend.dto.ChangePasswordRequest;
import com.immobilier.backend.dto.UpdateProfileRequest;
import com.immobilier.backend.dto.UserProfileDTO;
import com.immobilier.backend.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileDTO> getProfile() {
        return ResponseEntity.ok(profileService.getProfile());
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        try {
            return ResponseEntity.ok(profileService.updateProfile(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            profileService.changePassword(request);
            return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(profileService.uploadAvatar(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Erreur lors de l'upload."));
        }
    }

    @DeleteMapping("/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteAvatar() {
        profileService.deleteAvatar();
        return ResponseEntity.ok(Map.of("message", "Avatar supprimé."));
    }

    /**
     * GET /api/profile/avatar/{filename}
     * Serves the avatar image. Requires authentication — never open to anonymous requests
     * because avatar filenames contain the user ID.
     */
    @GetMapping("/avatar/{filename}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> serveAvatar(@PathVariable("filename") String filename) {
        try {
            byte[] bytes = profileService.getAvatarBytes(filename);
            String contentType = profileService.getAvatarContentType(filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, private")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
