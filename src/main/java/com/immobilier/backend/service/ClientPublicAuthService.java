package com.immobilier.backend.service;

import com.immobilier.backend.dto.AuthDTO;
import com.immobilier.backend.dto.ClientPublicProfileDTO;
import com.immobilier.backend.dto.ClientPublicRegisterRequest;
import com.immobilier.backend.dto.LoginRequest;
import com.immobilier.backend.dto.UpdateClientProfileRequest;
import com.immobilier.backend.entity.ClientInfo;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.ClientInfoRepository;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPublicAuthService {

    private final UserRepository userRepository;
    private final ClientInfoRepository clientInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthDTO register(ClientPublicRegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }
        if (userRepository.existsByTelephone(request.getTelephone())) {
            throw new IllegalArgumentException("Un compte existe déjà avec ce numéro de téléphone");
        }

        User user = new User();
        user.setEmail(email);
        user.setNom(request.getNom().trim());
        user.setPrenom(request.getPrenom().trim());
        user.setTelephone(request.getTelephone().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(RoleType.CLIENT_PUBLIC);
        user.setIsActive(true);
        User savedUser = userRepository.save(user);

        // Auto-create a ClientInfo so the user appears in Gestion des clients for SUPER_ADMIN
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setUser(savedUser);
        clientInfo.setCreatedBy(savedUser);
        clientInfo.setVisibilityType("PRIVATE_CLIENT");
        if (request.getBudgetEstime() != null && request.getBudgetEstime() > 0) {
            clientInfo.setBudgetEstime(request.getBudgetEstime());
        }
        String pays = request.getPays() != null ? request.getPays().trim() : null;
        String ville = request.getVille() != null ? request.getVille().trim() : null;
        if (pays != null && !pays.isBlank() && ville != null && !ville.isBlank()) {
            clientInfo.setZoneRecherchee(pays + ", " + ville);
        } else if (pays != null && !pays.isBlank()) {
            clientInfo.setZoneRecherchee(pays);
        }
        clientInfoRepository.save(clientInfo);

        log.info("New public client registered: {}", email);

        String token = jwtTokenProvider.generateToken(savedUser.getEmail(), savedUser.getRole().name(), savedUser.getId());
        return new AuthDTO(token, savedUser.getEmail(), savedUser.getRole().name(),
                savedUser.getNom(), savedUser.getPrenom(), savedUser.getId());
    }

    public AuthDTO login(LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getRole() != RoleType.CLIENT_PUBLIC && user.getRole() != RoleType.CLIENT) {
            throw new RuntimeException("Cet espace est réservé aux clients. Utilisez le portail Pro.");
        }
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new RuntimeException("Compte désactivé");
        }

        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        return new AuthDTO(token, user.getEmail(), user.getRole().name(),
                user.getNom(), user.getPrenom(), user.getId());
    }

    public ClientPublicProfileDTO toProfile(User user) {
        return new ClientPublicProfileDTO(user.getId(), user.getEmail(), user.getNom(),
                user.getPrenom(), user.getTelephone(), user.getRole().name());
    }

    @Transactional
    public ClientPublicProfileDTO updateProfile(Long userId, UpdateClientProfileRequest req) {
        if (userId == null) throw new IllegalArgumentException("ID utilisateur manquant");
        // Load a MANAGED entity inside the transaction to avoid LazyInitializationException
        // when Hibernate tries to merge a detached entity passed from the controller.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable"));

        String newEmail = req.getEmail().trim().toLowerCase();
        String newPrenom = req.getPrenom().trim();
        String newNom = req.getNom().trim();
        String newTel = (req.getTelephone() != null && !req.getTelephone().isBlank())
                ? req.getTelephone().trim() : null;

        // Email uniqueness — allow keeping the same address
        if (!newEmail.equals(user.getEmail())
                && userRepository.existsByEmailAndIdNot(newEmail, userId)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email.");
        }

        // Telephone uniqueness — allow keeping the same number, allow null
        if (newTel != null && !newTel.equals(user.getTelephone())
                && userRepository.existsByTelephoneAndIdNot(newTel, userId)) {
            throw new IllegalArgumentException("Un compte existe déjà avec ce numéro de téléphone.");
        }

        user.setPrenom(newPrenom);
        user.setNom(newNom);
        user.setEmail(newEmail);
        user.setTelephone(newTel);
        // Managed entity — dirty-checking flushes the changes at commit; save() is still safe
        User saved = userRepository.save(user);

        log.info("Public client {} updated their profile", saved.getId());
        return toProfile(saved);
    }
}
