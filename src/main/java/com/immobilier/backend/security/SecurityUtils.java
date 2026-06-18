// com/immobilier/backend/security/SecurityUtils.java
package com.immobilier.backend.security;

import com.immobilier.backend.entity.User;
import com.immobilier.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {
    
    private final UserRepository userRepository;
    
public User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new RuntimeException("Utilisateur non authentifié");
    }
    
    Object principal = authentication.getPrincipal();
    
    if (principal instanceof CustomUserDetails) {
        CustomUserDetails userDetails = (CustomUserDetails) principal;
        Long userId = userDetails.getUserId();
        
        
        if (userId == null) {
            throw new RuntimeException("ID utilisateur non trouvé dans le token");
        }
        
        return userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec ID: " + userId));
    }
    
    throw new RuntimeException("Impossible de récupérer l'utilisateur courant");
}
    
    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
    
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
    
    public boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }
    
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}