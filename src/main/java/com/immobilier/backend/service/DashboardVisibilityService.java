package com.immobilier.backend.service;

import com.immobilier.backend.dto.ClientCountDTO;
import com.immobilier.backend.dto.RecentClientsDTO;
import com.immobilier.backend.entity.ClientInfo;
import com.immobilier.backend.entity.ClientSharedAgency;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.ClientInfoRepository;
import com.immobilier.backend.repository.ClientSharedAgencyRepository;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardVisibilityService {

    private final UserRepository userRepository;
    private final ClientInfoRepository clientInfoRepository;
    private final ClientSharedAgencyRepository sharedAgencyRepository;
    private final SecurityUtils securityUtils;

    /**
     * Récupère le nombre de clients visibles pour l'utilisateur connecté
     * Les règles de visibilité sont appliquées DIRECTEMENT en base de données
     */
    @Transactional(readOnly = true)
    public ClientCountDTO getVisibleClientsCount() {
        User currentUser = securityUtils.getCurrentUser();
        log.info("Calcul du nombre de clients visibles pour: {} (rôle: {})", 
                 currentUser.getEmail(), currentUser.getRole());

        long count;

        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            // SUPER_ADMIN voit TOUS les clients (tous types)
            count = clientInfoRepository.count();
            log.info("SUPER_ADMIN - Nombre total de clients: {}", count);

        } else if (currentUser.getRole() == RoleType.ADMIN) {
            // ADMIN voit:
            // 1. Les AGENCY_CLIENT de son agence
            // 2. Les PRIVATE_CLIENT partagés avec son agence
            Long adminId = currentUser.getId();
            
            // Compter les AGENCY_CLIENT
            long agencyClientsCount = clientInfoRepository.countByAgencyAdminId(adminId);
            
            // Compter les PRIVATE_CLIENT partagés avec cet ADMIN
            long sharedPrivateClientsCount = sharedAgencyRepository.countByAdminId(adminId);
            
            count = agencyClientsCount + sharedPrivateClientsCount;
            log.info("ADMIN {} - Clients agence: {}, Clients privés partagés: {}, Total: {}", 
                     adminId, agencyClientsCount, sharedPrivateClientsCount, count);

        } else {
            // Pour les enfants (COMMERCIAL, RESPONSABLE_COMMERCIAL, etc.)
            // Ils voient uniquement les AGENCY_CLIENT de leur agence
            Long agencyAdminId = getAgencyAdminId(currentUser);
            count = clientInfoRepository.countByAgencyAdminId(agencyAdminId);
            log.info("Enfant (rôle: {}) - Clients de l'agence {}: {}", 
                     currentUser.getRole(), agencyAdminId, count);
        }

        return ClientCountDTO.builder()
                .count(count)
                .role(currentUser.getRole().name())
                .build();
    }

    /**
     * Récupère les clients récents visibles pour l'utilisateur connecté
     */
    @Transactional(readOnly = true)
    public List<RecentClientsDTO> getRecentVisibleClients(int limit) {
        User currentUser = securityUtils.getCurrentUser();
        log.info("Récupération des {} clients récents visibles pour: {}", limit, currentUser.getEmail());

        List<ClientInfo> visibleClients;

        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            // SUPER_ADMIN voit tous les clients récents
            visibleClients = clientInfoRepository.findTop6ByOrderByCreatedAtDesc();
            log.info("SUPER_ADMIN - Récupération de {} clients", visibleClients.size());

        } else if (currentUser.getRole() == RoleType.ADMIN) {
            // ADMIN voit ses clients agence + clients privés partagés
            visibleClients = getVisibleClientsForAdmin(currentUser.getId(), limit);
            log.info("ADMIN {} - Récupération de {} clients visibles", 
                     currentUser.getId(), visibleClients.size());

        } else {
            // Enfants: uniquement les clients de leur agence
            Long agencyAdminId = getAgencyAdminId(currentUser);
            visibleClients = clientInfoRepository.findTop6ByAgencyAdminIdOrderByCreatedAtDesc(agencyAdminId);
            log.info("Enfant - Récupération de {} clients de l'agence {}", 
                     visibleClients.size(), agencyAdminId);
        }

        return visibleClients.stream()
                .map(this::convertToRecentClientsDTO)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Récupère les clients visibles pour un ADMIN (agence + partagés)
     */
    private List<ClientInfo> getVisibleClientsForAdmin(Long adminId, int limit) {
        // Récupérer les ID des clients partagés avec cet admin
        List<Long> sharedClientIds = sharedAgencyRepository.findClientIdsByAdminId(adminId);
        
        if (sharedClientIds.isEmpty()) {
            // Pas de clients partagés, uniquement les clients de l'agence
            return clientInfoRepository.findTop6ByAgencyAdminIdOrderByCreatedAtDesc(adminId);
        }
        
        // Clients agence + clients partagés
        return clientInfoRepository.findTop6ByAgencyAdminIdOrIdInOrderByCreatedAtDesc(adminId, sharedClientIds);
    }

    /**
     * Récupère l'ID de l'ADMIN propriétaire pour un utilisateur
     */
    private Long getAgencyAdminId(User user) {
        if (user.getRole() == RoleType.ADMIN) {
            return user.getId();
        }
        
        // Remonter la hiérarchie pour trouver l'ADMIN
        User current = user;
        while (current != null && current.getParent() != null) {
            if (current.getParent().getRole() == RoleType.ADMIN) {
                return current.getParent().getId();
            }
            current = current.getParent();
        }
        
        throw new IllegalStateException("Impossible de trouver l'ADMIN parent pour l'utilisateur: " + user.getId());
    }

    /**
     * Convertit ClientInfo en RecentClientsDTO
     */
    private RecentClientsDTO convertToRecentClientsDTO(ClientInfo clientInfo) {
        User user = clientInfo.getUser();
        
        return RecentClientsDTO.builder()
                .id(user.getId())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .email(user.getEmail())
                .telephone(user.getTelephone())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .visibilityType(clientInfo.getVisibilityType())
                .agencyAdminId(clientInfo.getAgencyAdminId())
                .build();
    }
}