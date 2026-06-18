package com.immobilier.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.immobilier.backend.dto.AuthDTO;
import com.immobilier.backend.dto.LoginRequest;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.JwtTokenProvider;
import com.immobilier.backend.enums.RoleType;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AvatarUrlBuilder avatarUrlBuilder;

    public AuthDTO login(LoginRequest request) {
        // Authentification
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Récupération de l'utilisateur
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!user.getIsActive()) {
            throw new RuntimeException("Compte désactivé");
        }

        // Génération JWT
        String token = jwtTokenProvider.generateToken(
                user.getEmail(),
                user.getRole().name(),
                user.getId()
        );

        return new AuthDTO(
                token,
                user.getEmail(),
                user.getRole().name(),
                user.getNom(),
                user.getPrenom(),
                user.getId(),
                avatarUrlBuilder.build(user.getProfileImagePath())
        );
    }

    // Création d’un super admin
    public void initSuperAdmin() {
        if (userRepository.findByEmail("superadmin@immobilier.com").isEmpty()) {
            User superAdmin = new User();
            superAdmin.setEmail("superadmin@immobilier.com");
            superAdmin.setPassword(passwordEncoder.encode("admin123"));
            superAdmin.setNom("Super");
            superAdmin.setPrenom("Admin");
            superAdmin.setTelephone("+212600000000");
            superAdmin.setRole(RoleType.SUPER_ADMIN);
            superAdmin.setIsActive(true);

            userRepository.save(superAdmin);
            System.out.println("✅ Super Admin créé avec succès!");
        }
    }
}
