package com.immobilier.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.immobilier.backend.dto.AuthDTO;
import com.immobilier.backend.dto.LoginRequest;
import com.immobilier.backend.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthDTO> login(@RequestBody LoginRequest request) {
        AuthDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/init-super-admin")
    public ResponseEntity<String> initSuperAdmin() {
        authService.initSuperAdmin();
        return ResponseEntity.ok("Super Admin initialisé avec succès");
    }
}
