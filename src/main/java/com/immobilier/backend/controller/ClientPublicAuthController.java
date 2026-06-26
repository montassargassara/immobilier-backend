package com.immobilier.backend.controller;

import com.immobilier.backend.dto.AuthDTO;
import com.immobilier.backend.dto.ClientPublicProfileDTO;
import com.immobilier.backend.dto.ClientPublicRegisterRequest;
import com.immobilier.backend.dto.LoginRequest;
import com.immobilier.backend.dto.UpdateClientProfileRequest;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.ClientPublicAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/client/auth")
@RequiredArgsConstructor
public class ClientPublicAuthController {

    private final ClientPublicAuthService clientAuth;
    private final SecurityUtils securityUtils;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody ClientPublicRegisterRequest request) {
        try {
            AuthDTO out = clientAuth.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Registration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Une erreur est survenue. Réessayez plus tard."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(clientAuth.login(request));
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Email ou mot de passe incorrect"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT_PUBLIC')")
    public ResponseEntity<?> me() {
        User user = securityUtils.getCurrentUser();
        if (user == null || user.getRole() != RoleType.CLIENT_PUBLIC) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(clientAuth.toProfile(user));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('CLIENT_PUBLIC')")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateClientProfileRequest request) {
        try {
            User user = securityUtils.getCurrentUser();
            if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            ClientPublicProfileDTO dto = clientAuth.updateProfile(user, request);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Profile update failed for user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Une erreur est survenue. Réessayez plus tard."));
        }
    }
}
