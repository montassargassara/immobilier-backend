package com.immobilier.backend.service;

import com.immobilier.backend.dto.ChangePasswordRequest;
import com.immobilier.backend.dto.UpdateProfileRequest;
import com.immobilier.backend.dto.UserProfileDTO;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final AvatarUrlBuilder avatarUrlBuilder;

    @Value("${file.upload.avatars-dir:uploads/avatars}")
    private String avatarsDir;

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 5L * 1024 * 1024; // 5 MB

    // ── Get profile ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile() {
        return toDTO(securityUtils.getCurrentUser());
    }

    // ── Update profile ────────────────────────────────────────────────────────

    @Transactional
    public UserProfileDTO updateProfile(UpdateProfileRequest request) {
        User user = securityUtils.getCurrentUser();

        String newEmail = request.getEmail().trim().toLowerCase();
        if (!user.getEmail().equalsIgnoreCase(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("Cet email est déjà utilisé par un autre compte.");
        }

        String newTel = (request.getTelephone() != null && !request.getTelephone().isBlank())
                ? request.getTelephone().trim() : null;
        if (newTel != null && !newTel.equals(user.getTelephone())
                && userRepository.existsByTelephone(newTel)) {
            throw new IllegalArgumentException("Ce numéro de téléphone est déjà utilisé.");
        }

        user.setPrenom(request.getPrenom().trim());
        user.setNom(request.getNom().trim());
        user.setEmail(newEmail);
        user.setTelephone(newTel);

        return toDTO(userRepository.save(user));
    }

    // ── Change password ───────────────────────────────────────────────────────

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = securityUtils.getCurrentUser();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect.");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Les nouveaux mots de passe ne correspondent pas.");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'ancien.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // ── Avatar upload ─────────────────────────────────────────────────────────

    @Transactional
    public UserProfileDTO uploadAvatar(MultipartFile file) throws IOException {
        User user = securityUtils.getCurrentUser();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("L'image ne doit pas dépasser 5 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Format non autorisé. Utilisez JPG, PNG ou WEBP.");
        }

        // Delete old avatar file
        if (user.getProfileImagePath() != null) {
            deleteAvatarFile(user.getProfileImagePath());
        }

        // Persist new file
        Path dir = Paths.get(avatarsDir);
        Files.createDirectories(dir);

        String ext = switch (contentType) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default           -> ".jpg";
        };
        String filename = "avatar_" + user.getId() + "_" + System.currentTimeMillis() + ext;
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

        user.setProfileImagePath(filename);
        return toDTO(userRepository.save(user));
    }

    // ── Avatar delete ─────────────────────────────────────────────────────────

    @Transactional
    public void deleteAvatar() {
        User user = securityUtils.getCurrentUser();
        if (user.getProfileImagePath() != null) {
            deleteAvatarFile(user.getProfileImagePath());
            user.setProfileImagePath(null);
            userRepository.save(user);
        }
    }

    // ── Serve avatar bytes ────────────────────────────────────────────────────

    public byte[] getAvatarBytes(String filename) throws IOException {
        // Sanitize: reject any path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Nom de fichier invalide.");
        }
        Path file = Paths.get(avatarsDir).resolve(filename).normalize();
        if (!Files.exists(file)) {
            throw new RuntimeException("Avatar introuvable.");
        }
        return Files.readAllBytes(file);
    }

    public String getAvatarContentType(String filename) {
        if (filename.endsWith(".png"))  return "image/png";
        if (filename.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void deleteAvatarFile(String filename) {
        try {
            Files.deleteIfExists(Paths.get(avatarsDir).resolve(filename));
        } catch (IOException e) {
            log.warn("Could not delete avatar file: {}", filename);
        }
    }

    private UserProfileDTO toDTO(User user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setPrenom(user.getPrenom());
        dto.setNom(user.getNom());
        dto.setEmail(user.getEmail());
        dto.setTelephone(user.getTelephone());
        dto.setRole(user.getRole().name());
        dto.setAvatarUrl(avatarUrlBuilder.build(user.getProfileImagePath()));
        return dto;
    }
}
