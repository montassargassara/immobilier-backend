// com.immobilier.backend.service.ClientManagementService.java
package com.immobilier.backend.service;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.entity.AffiliateProfile;
import com.immobilier.backend.entity.AffiliateRegion;
import com.immobilier.backend.entity.ClientInfo;
import com.immobilier.backend.entity.ClientNote;
import com.immobilier.backend.entity.ClientSharedAgency;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.AffiliateStatus;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.AffiliateProfileRepository;
import com.immobilier.backend.repository.AffiliateRegionRepository;
import com.immobilier.backend.repository.AffiliateTransactionRepository;
import com.immobilier.backend.repository.ClientInfoRepository;
import com.immobilier.backend.repository.ClientNoteRepository;
import com.immobilier.backend.repository.ClientSharedAgencyRepository;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientManagementService {

    private final UserRepository userRepository;
    private final ClientInfoRepository clientInfoRepository;
    private final ClientNoteRepository clientNoteRepository;
    private final SecurityUtils securityUtils;
    private final ClientSharedAgencyRepository sharedAgencyRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final AffiliateProfileRepository affiliateProfileRepository;
    private final AffiliateRegionRepository affiliateRegionRepository;
    private final AffiliateTransactionRepository affiliateTransactionRepository;

    private static final List<RoleType> CLIENT_ROLES = Arrays.asList(RoleType.CLIENT, RoleType.AFFILIATE, RoleType.CLIENT_PUBLIC);
    // ========== CREATE ==========
    
    /**
     * Récupère l'ID de l'ADMIN propriétaire pour un utilisateur créateur
     */
    private Long getAgencyAdminId(User creator) {
        if (creator.getRole() == RoleType.ADMIN) {
            return creator.getId();
        }
        Optional<User> topAdmin = userRepository.findTopAdminAncestor(creator.getId());
        if (topAdmin.isPresent()) {
            return topAdmin.get().getId();
        }
        if (creator.getParent() != null && creator.getParent().getRole() == RoleType.ADMIN) {
            return creator.getParent().getId();
        }
        throw new IllegalStateException("Impossible de déterminer l'agence pour l'utilisateur: " + creator.getId());
    }

    @Transactional
    public ClientDTO createClient(CreateClientRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        log.info("Création d'un nouveau client par: {} - Type: {}", currentUser.getEmail(), request.getVisibilityType());
        
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Un utilisateur avec cet email existe déjà");
        }
        
        RoleType role = "AFFILIATE".equalsIgnoreCase(request.getClientType()) ? RoleType.AFFILIATE : RoleType.CLIENT_PUBLIC;
        
        // Créer l'utilisateur
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setTelephone(request.getTelephone());
        user.setRole(role);
        user.setIsActive(true);
        user.setParent(currentUser);
        
        User savedUser = userRepository.save(user);
        log.info("Utilisateur client créé avec ID: {}", savedUser.getId());
        
        // Gérer la visibilité
        String visibilityType = request.getVisibilityType();
        Long agencyAdminId = null;
        
        if ("AGENCY_CLIENT".equals(visibilityType)) {
            if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
                // SUPER_ADMIN: use explicitly chosen target, or null (visible to SUPER_ADMIN only)
                agencyAdminId = request.getTargetAgencyAdminId();
            } else {
                agencyAdminId = getAgencyAdminId(currentUser);
            }
        }
        
        // Créer les infos client
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setUser(savedUser);
        clientInfo.setCreatedBy(currentUser);
        clientInfo.setVisibilityType(visibilityType != null ? visibilityType : "AGENCY_CLIENT");
        clientInfo.setAgencyAdminId(agencyAdminId);
        clientInfo.setBudgetEstime(request.getBudgetEstime());
        clientInfo.setZoneRecherchee(request.getZoneRecherchee());
        
        if (request.getCommercialId() != null) {
            User commercial = userRepository.findById(request.getCommercialId())
                .orElseThrow(() -> new IllegalArgumentException("Commercial non trouvé"));
            clientInfo.setCommercial(commercial);
        }
        
        if (role == RoleType.AFFILIATE) {
            String codeAffiliation = request.getCodeAffiliation();
            if (codeAffiliation == null || codeAffiliation.isEmpty()) {
                codeAffiliation = generateAffiliateCode();
            }
            clientInfo.setCodeAffiliation(codeAffiliation);
            clientInfo.setTauxCommission(request.getTauxCommission() != null ? request.getTauxCommission() : 5.0);
            clientInfo.setSource(request.getSource());
        }
        
        ClientInfo savedInfo = clientInfoRepository.save(clientInfo);

        // Pour les affiliés créés par l'admin, créer AffiliateProfile (ACTIVE) + AffiliateRegion
        if (role == RoleType.AFFILIATE) {
            AffiliateProfile profile = new AffiliateProfile();
            profile.setUser(savedUser);
            profile.setStatus(AffiliateStatus.ACTIVE);
            affiliateProfileRepository.save(profile);

            String zone = request.getZoneRecherchee();
            if (zone != null && !zone.isBlank()) {
                String[] parts = zone.split(",", 2);
                String country = parts[0].trim();
                String city = parts.length > 1 ? parts[1].trim() : parts[0].trim();

                AffiliateRegion region = new AffiliateRegion();
                region.setAffiliate(savedUser);
                region.setRegionName(city.toLowerCase());
                region.setCountry(country);
                region.setCity(city);
                region.setRegionDescription(country);
                region.setIsActive(true);
                affiliateRegionRepository.save(region);
                log.info("AffiliateRegion créée: {} / {} pour l'affilié ID {}", country, city, savedUser.getId());
            }
        }

        // Pour PRIVATE_CLIENT, partager avec les agences sélectionnées
        if ("PRIVATE_CLIENT".equals(visibilityType) && request.getSharedAgencyIds() != null && !request.getSharedAgencyIds().isEmpty()) {
            sharePrivateClientWithAgencies(savedInfo, currentUser, request.getSharedAgencyIds());
        }

        return convertToDTO(savedUser, savedInfo);
    }

        private void sharePrivateClientWithAgencies(ClientInfo client, User sharedBy, List<Long> agencyAdminIds) {
        for (Long adminId : agencyAdminIds) {
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin non trouvé: " + adminId));
            
            if (admin.getRole() != RoleType.ADMIN) {
                throw new IllegalArgumentException("L'utilisateur " + adminId + " n'est pas un ADMIN");
            }
            
            ClientSharedAgency sharedAgency = new ClientSharedAgency();
            sharedAgency.setClient(client);
            sharedAgency.setAdmin(admin);
            sharedAgency.setSharedBy(sharedBy);
            sharedAgencyRepository.save(sharedAgency);
        }
        log.info("Client privé partagé avec {} agences", agencyAdminIds.size());
    }
    
    private String generateAffiliateCode() {
        return "AFF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ========== READ ==========
    
    @Transactional(readOnly = true)
    public Page<ClientDTO> getAllClients(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        log.info("Récupération des clients pour: {} (rôle: {})", currentUser.getEmail(), currentUser.getRole());
        
        Page<ClientInfo> clientInfos;
        
        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            // SUPER_ADMIN voit tout
            clientInfos = clientInfoRepository.findAllForSuperAdmin(pageable);
            log.info("SUPER_ADMIN - Affichage de tous les clients");
        } else if (currentUser.getRole() == RoleType.ADMIN) {
            // ADMIN voit: AGENCY_CLIENT de son agence + PRIVATE_CLIENT partagés avec lui
            clientInfos = clientInfoRepository.findVisibleClientsForAdmin(currentUser.getId(), pageable);
            log.info("ADMIN - Affichage des clients visibles");
        } else {
            // Enfants (COMMERCIAL, etc.) voient uniquement les AGENCY_CLIENT de leur agence
            Long agencyAdminId = getAgencyAdminId(currentUser);
            clientInfos = clientInfoRepository.findAgencyClientsByAgencyAdminId(agencyAdminId, pageable);
            log.info("Enfant - Affichage des clients de l'agence ID: {}", agencyAdminId);
        }
        
        return clientInfos.map(info -> {
            ClientDTO dto = convertToDTO(info.getUser(), info);
            if ("PRIVATE_CLIENT".equals(info.getVisibilityType())) {
                List<Long> sharedAgencyIds = clientInfoRepository.findSharedAgencyIdsByClientId(info.getId());
                dto.setSharedWithAgencyIds(sharedAgencyIds);
            }
            return dto;
        });
    }
    
        @Transactional
    public void sharePrivateClientWithAgency(Long clientId, Long adminId) {
        User currentUser = securityUtils.getCurrentUser();
        
        if (currentUser.getRole() != RoleType.SUPER_ADMIN) {
            throw new SecurityException("Seul un SUPER_ADMIN peut partager des clients privés");
        }
        
        ClientInfo client = clientInfoRepository.findById(clientId)
            .orElseThrow(() -> new IllegalArgumentException("Client non trouvé"));
        
        if (!"PRIVATE_CLIENT".equals(client.getVisibilityType())) {
            throw new IllegalArgumentException("Seuls les clients privés peuvent être partagés");
        }
        
        User admin = userRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin non trouvé"));
        
        if (admin.getRole() != RoleType.ADMIN) {
            throw new IllegalArgumentException("L'utilisateur n'est pas un ADMIN");
        }
        
        if (!clientInfoRepository.isSharedWithAgency(clientId, adminId)) {
            ClientSharedAgency sharedAgency = new ClientSharedAgency();
            sharedAgency.setClient(client);
            sharedAgency.setAdmin(admin);
            sharedAgency.setSharedBy(currentUser);
            sharedAgencyRepository.save(sharedAgency);
            log.info("Client privé {} partagé avec l'agence {}", clientId, adminId);
        }
    }

        @Transactional
    public void revokePrivateClientSharing(Long clientId, Long adminId) {
        User currentUser = securityUtils.getCurrentUser();
        
        if (currentUser.getRole() != RoleType.SUPER_ADMIN) {
            throw new SecurityException("Seul un SUPER_ADMIN peut révoquer le partage");
        }
        
        sharedAgencyRepository.deleteByClientIdAndAdminId(clientId, adminId);
        log.info("Partage du client {} avec l'agence {} révoqué", clientId, adminId);
    }
    
    public List<UserDTO> getAvailableAgenciesForSharing(Long clientId) {
        User currentUser = securityUtils.getCurrentUser();
        
        if (currentUser.getRole() != RoleType.SUPER_ADMIN) {
            throw new SecurityException("Seul un SUPER_ADMIN peut voir les agences disponibles");
        }
        
        ClientInfo client = clientInfoRepository.findById(clientId)
            .orElseThrow(() -> new IllegalArgumentException("Client non trouvé"));
        
        if (!"PRIVATE_CLIENT".equals(client.getVisibilityType())) {
            throw new IllegalArgumentException("Seuls les clients privés peuvent être partagés");
        }
        
        List<User> allAdmins = userRepository.findByRole(RoleType.ADMIN);
        List<Long> alreadySharedIds = clientInfoRepository.findSharedAgencyIdsByClientId(clientId);
        
        return allAdmins.stream()
            .filter(admin -> !alreadySharedIds.contains(admin.getId()))
            .map(this::convertToUserDTO)
            .collect(Collectors.toList());
    }

        private UserDTO convertToUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setNom(user.getNom());
        dto.setPrenom(user.getPrenom());
        dto.setRole(user.getRole());
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<ClientDTO> searchClients(String keyword, Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        log.info("Recherche de clients avec mot-clé: {} par: {}", keyword, currentUser.getEmail());
        
        Page<ClientInfo> clientInfos;
        
        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            clientInfos = clientInfoRepository.searchByKeywordForSuperAdmin(keyword, pageable);
        } else {
            Long agencyAdminId = getAgencyAdminId(currentUser);
            clientInfos = clientInfoRepository.searchByKeywordForAgency(agencyAdminId, keyword, pageable);
        }
        
        return clientInfos.map(info -> convertToDTO(info.getUser(), info));
    }
    
    public Page<ClientDTO> getClientsByCommercial(Long commercialId, Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        log.info("Récupération des clients du commercial ID: {}", commercialId);
        
        User commercial = userRepository.findById(commercialId)
            .orElseThrow(() -> new IllegalArgumentException("Commercial non trouvé"));

        Page<ClientInfo> clientInfos;
        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            clientInfos = clientInfoRepository.findByCommercial(CLIENT_ROLES, commercialId, pageable);
        } else {
            Long agencyAdminId = getAgencyAdminId(currentUser);
            Long commercialAgencyId = getAgencyAdminId(commercial);
            if (!commercialAgencyId.equals(agencyAdminId)) {
                return Page.empty();
            }
            clientInfos = clientInfoRepository.findByCommercial(CLIENT_ROLES, commercialId, pageable);
        }
        
        return clientInfos.map(info -> convertToDTO(info.getUser(), info));
    }
    
public Page<ClientDTO> getClientsByBudgetRange(Double minBudget, Double maxBudget, Pageable pageable) {
    User currentUser = securityUtils.getCurrentUser();
    log.info("Récupération des clients avec budget entre {} et {} par: {}", minBudget, maxBudget, currentUser.getEmail());
    
    Page<ClientInfo> clientInfos;
    
    if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
        clientInfos = clientInfoRepository.findByBudgetRange(CLIENT_ROLES, minBudget, maxBudget, pageable);
    } else {
        Long agencyAdminId = getAgencyAdminId(currentUser);
        clientInfos = clientInfoRepository.findByAgencyAndBudgetRange(agencyAdminId, minBudget, maxBudget, pageable);
    }
    
    return clientInfos.map(info -> convertToDTO(info.getUser(), info));
}

public Page<ClientDTO> getBuyers(Pageable pageable) {
    User currentUser = securityUtils.getCurrentUser();
    log.info("Récupération des clients acheteurs par: {}", currentUser.getEmail());
    
    Page<ClientInfo> clientInfos;
    
    if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
        clientInfos = clientInfoRepository.findBuyers(CLIENT_ROLES, pageable);
    } else {
        Long agencyAdminId = getAgencyAdminId(currentUser);
        clientInfos = clientInfoRepository.findBuyersByAgency(agencyAdminId, pageable);
    }
    
    return clientInfos.map(info -> convertToDTO(info.getUser(), info));
}
    
    
public ClientDTO getClientById(Long id) {
    User currentUser = securityUtils.getCurrentUser();
    log.info("Récupération du client ID: {} par: {}", id, currentUser.getEmail());
    
    Optional<ClientInfo> clientInfoOpt = clientInfoRepository.findById(id);
    
    if (clientInfoOpt.isEmpty()) {
        throw new IllegalArgumentException("Client non trouvé");
    }
    
    ClientInfo clientInfo = clientInfoOpt.get();
    User user = clientInfo.getUser();
    
    if (!CLIENT_ROLES.contains(user.getRole())) {
        throw new IllegalArgumentException("Cet utilisateur n'est pas un client");
    }
    
    // Vérification de visibilité par hiérarchie
    if (!canViewClient(currentUser, clientInfo)) {
        throw new SecurityException("Vous n'avez pas accès à ce client");
    }
    
    return convertToDTO(user, clientInfo);
}
    
        /**
     * Vérifie si un utilisateur peut voir un client basé sur la hiérarchie
     */
    private boolean canViewClient(User viewer, ClientInfo clientInfo) {
        // SUPER_ADMIN voit tout
        if (viewer.getRole() == RoleType.SUPER_ADMIN) {
            return true;
        }
        
        // L'utilisateur voit les clients de son agence
        Long viewerAgencyAdminId = getAgencyAdminId(viewer);
        return clientInfo.getAgencyAdminId().equals(viewerAgencyAdminId);
    }

private ClientInfo createEmptyClientInfo(User user) {
    User currentUser = securityUtils.getCurrentUser();
    ClientInfo info = new ClientInfo();
    info.setUser(user);
    info.setCreatedBy(currentUser);
    info.setAgencyAdminId(getAgencyAdminId(currentUser));
    return clientInfoRepository.save(info);
}

    // ========== UPDATE ==========
    
    @Transactional
    public ClientDTO updateClient(Long id, UpdateClientRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        log.info("Mise à jour du client ID: {} par: {}", id, currentUser.getEmail());
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Client non trouvé"));
        
        if (!CLIENT_ROLES.contains(user.getRole())) {
            throw new IllegalArgumentException("Cet utilisateur n'est pas un client");
        }
        
        ClientInfo clientInfo = clientInfoRepository.findByUserId(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Info client non trouvée"));
        
        // Vérifier que l'utilisateur peut modifier ce client
        if (!canModifyClient(currentUser, clientInfo)) {
            throw new SecurityException("Vous n'avez pas les droits pour modifier ce client");
        }
        
        // Mise à jour des infos utilisateur
        if (request.getNom() != null) user.setNom(request.getNom());
        if (request.getPrenom() != null) user.setPrenom(request.getPrenom());
        if (request.getTelephone() != null) user.setTelephone(request.getTelephone());
        if (request.getIsActive() != null) user.setIsActive(request.getIsActive());
        
        User updatedUser = userRepository.save(user);
        
        // Mise à jour des infos client
        if (request.getBudgetEstime() != null) clientInfo.setBudgetEstime(request.getBudgetEstime());
        if (request.getZoneRecherchee() != null) clientInfo.setZoneRecherchee(request.getZoneRecherchee());
        
        if (request.getCommercialId() != null) {
            User commercial = userRepository.findById(request.getCommercialId())
                .orElseThrow(() -> new IllegalArgumentException("Commercial non trouvé"));
            clientInfo.setCommercial(commercial);
        }
        
        // Mise à jour des champs affilié
        if (user.getRole() == RoleType.AFFILIATE) {
            if (request.getCodeAffiliation() != null) clientInfo.setCodeAffiliation(request.getCodeAffiliation());
            if (request.getTauxCommission() != null) clientInfo.setTauxCommission(request.getTauxCommission());
            if (request.getSource() != null) clientInfo.setSource(request.getSource());

            // Country + city are the new affiliate-zone fields. When provided, they
            // (a) rebuild the legacy zoneRecherchee string and
            // (b) update (or create) the AffiliateRegion row that drives zone-based
            // property visibility in AffiliateService.
            String newCountry = request.getCountry();
            String newCity = request.getCity();
            boolean hasZoneUpdate = newCountry != null && !newCountry.isBlank()
                                 && newCity    != null && !newCity.isBlank();
            if (hasZoneUpdate) {
                String trimmedCountry = newCountry.trim();
                String trimmedCity = newCity.trim();
                clientInfo.setZoneRecherchee(trimmedCountry + ", " + trimmedCity);

                List<AffiliateRegion> existing = affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(user.getId());
                AffiliateRegion region = existing.isEmpty() ? new AffiliateRegion() : existing.get(0);
                if (region.getAffiliate() == null) region.setAffiliate(user);
                region.setCountry(trimmedCountry);
                region.setCity(trimmedCity);
                region.setRegionName(trimmedCity.toLowerCase());
                region.setRegionDescription(trimmedCountry);
                region.setIsActive(true);
                affiliateRegionRepository.save(region);
                log.info("AffiliateRegion mise à jour: {} / {} pour l'affilié ID {}", trimmedCountry, trimmedCity, user.getId());
            }
        }

        ClientInfo updatedInfo = clientInfoRepository.save(clientInfo);

        return convertToDTO(updatedUser, updatedInfo);
    }
    
    private boolean canModifyClient(User modifier, ClientInfo clientInfo) {
        // SUPER_ADMIN peut tout modifier
        if (modifier.getRole() == RoleType.SUPER_ADMIN) {
            return true;
        }
        
        // Le créateur peut modifier ses clients
        if (clientInfo.getCreatedBy() != null && clientInfo.getCreatedBy().getId().equals(modifier.getId())) {
            return true;
        }
        
        // L'ADMIN de l'agence peut modifier tous les clients de son agence
        Long modifierAgencyAdminId = getAgencyAdminId(modifier);
        return clientInfo.getAgencyAdminId().equals(modifierAgencyAdminId);
    }
    
@Transactional
public ClientDTO assignCommercial(Long clientId, Long commercialId) {
    User currentUser = securityUtils.getCurrentUser();
    log.info("Assignation du commercial ID: {} au client ID: {} par: {}", commercialId, clientId, currentUser.getEmail());
    
    ClientInfo clientInfo = clientInfoRepository.findById(clientId)
        .orElseThrow(() -> new IllegalArgumentException("Client non trouvé"));
    
    User commercial = userRepository.findById(commercialId)
        .orElseThrow(() -> new IllegalArgumentException("Commercial non trouvé"));
    
    if (commercial.getRole() != RoleType.COMMERCIAL && commercial.getRole() != RoleType.RESPONSABLE_COMMERCIAL) {
        throw new IllegalArgumentException("L'utilisateur assigné n'est pas un commercial");
    }
    
    // Vérifier que l'utilisateur peut modifier ce client
    if (!canModifyClient(currentUser, clientInfo)) {
        throw new SecurityException("Vous n'avez pas les droits pour assigner un commercial à ce client");
    }
    
    // Vérifier que le commercial est dans la même agence
    Long commercialAgencyId = getAgencyAdminId(commercial);
    if (!clientInfo.getAgencyAdminId().equals(commercialAgencyId)) {
        throw new IllegalArgumentException("Ce commercial n'appartient pas à votre agence");
    }
    
    clientInfo.setCommercial(commercial);
    ClientInfo updatedInfo = clientInfoRepository.save(clientInfo);
    
    return convertToDTO(clientInfo.getUser(), updatedInfo);
}
    
@Transactional
public ClientDTO toggleClientStatus(Long id) {
    User currentUser = securityUtils.getCurrentUser();
    log.info("Toggle statut du client ID: {} par: {}", id, currentUser.getEmail());
    
    ClientInfo clientInfo = clientInfoRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Client non trouvé"));
    
    // Vérifier que l'utilisateur peut modifier ce client
    if (!canModifyClient(currentUser, clientInfo)) {
        throw new SecurityException("Vous n'avez pas les droits pour modifier le statut de ce client");
    }
    
    User user = clientInfo.getUser();
    user.setIsActive(!user.getIsActive());
    User updatedUser = userRepository.save(user);
    
    return convertToDTO(updatedUser, clientInfo);
}

    // ========== DELETE ==========
    
    @Transactional
    public void deleteClient(Long id) {
        log.info("Suppression (désactivation) du client ID: {}", id);
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Client non trouvé"));
        
        
        user.setIsActive(false);
        userRepository.save(user);
    }

    // ========== NOTES ==========
    
    @Transactional
    public ClientNoteDTO addClientNote(Long clientId, Long commercialId, String note) {
        log.info("Ajout d'une note pour le client ID: {} par commercial ID: {}", clientId, commercialId);
        
        User client = userRepository.findById(clientId)
            .orElseThrow(() -> new IllegalArgumentException("Client non trouvé"));
        
        User commercial = userRepository.findById(commercialId)
            .orElseThrow(() -> new IllegalArgumentException("Commercial non trouvé"));
        
        ClientNote clientNote = new ClientNote();
        clientNote.setClient(client);
        clientNote.setCommercial(commercial);
        clientNote.setNote(note);
        
        ClientNote savedNote = clientNoteRepository.save(clientNote);
        
        ClientNoteDTO dto = new ClientNoteDTO();
        dto.setId(savedNote.getId());
        dto.setClientId(clientId);
        dto.setCommercialId(commercialId);
        dto.setCommercialNom(commercial.getNom());
        dto.setCommercialPrenom(commercial.getPrenom());
        dto.setNote(note);
        dto.setCreatedAt(savedNote.getCreatedAt());
        
        return dto;
    }
    
    public List<ClientNoteDTO> getClientNotes(Long clientId) {
        log.info("Récupération des notes pour le client ID: {}", clientId);
        
        List<ClientNote> notes = clientNoteRepository.findByClientId(clientId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        
        return notes.stream().map(note -> {
            ClientNoteDTO dto = new ClientNoteDTO();
            dto.setId(note.getId());
            dto.setClientId(clientId);
            dto.setCommercialId(note.getCommercial().getId());
            dto.setCommercialNom(note.getCommercial().getNom());
            dto.setCommercialPrenom(note.getCommercial().getPrenom());
            dto.setNote(note.getNote());
            dto.setCreatedAt(note.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
    }

    // ========== STATISTICS ==========
    
// Dans ClientManagementService.java, corrigez la méthode getClientStats:

public Map<String, Object> getClientStats() {
    User currentUser = securityUtils.getCurrentUser();
    Long agencyAdminId = currentUser.getRole() == RoleType.SUPER_ADMIN ? null : getAgencyAdminId(currentUser);
    
    Map<String, Object> stats = new HashMap<>();
    
    long totalClientsNormaux, totalClientsAffilies, totalClientsActifs;
    
    if (agencyAdminId == null) {
        // SUPER_ADMIN - stats globales
        totalClientsNormaux = clientInfoRepository.countByRole(RoleType.CLIENT)
                            + clientInfoRepository.countByRole(RoleType.CLIENT_PUBLIC);
        totalClientsAffilies = clientInfoRepository.countByRole(RoleType.AFFILIATE);
        totalClientsActifs = clientInfoRepository.countActiveByUserRoleSingle(RoleType.CLIENT)
                           + clientInfoRepository.countActiveByUserRoleSingle(RoleType.CLIENT_PUBLIC)
                           + clientInfoRepository.countActiveByUserRoleSingle(RoleType.AFFILIATE);
        stats.put("clientsAcheteurs", 0);
    } else {
        // ADMIN ou enfant - stats de l'agence uniquement
        totalClientsNormaux = clientInfoRepository.countByAgencyAdminIdAndRole(agencyAdminId, RoleType.CLIENT)
                            + clientInfoRepository.countByAgencyAdminIdAndRole(agencyAdminId, RoleType.CLIENT_PUBLIC);
        totalClientsAffilies = clientInfoRepository.countByAgencyAdminIdAndRole(agencyAdminId, RoleType.AFFILIATE);
        totalClientsActifs = clientInfoRepository.countActiveByAgencyAdminId(agencyAdminId);
        stats.put("clientsAcheteurs", 0);
    }
    
    LocalDateTime now = LocalDateTime.now();
    int currentMonth = now.getMonthValue();
    int currentYear = now.getYear();
    
    Double ventesMois = clientInfoRepository.sumVentesByMonth(currentMonth, currentYear, agencyAdminId);
    Double commissionsMois = clientInfoRepository.sumCommissionAffiliatesByMonth(currentMonth, currentYear, agencyAdminId);
    
    stats.put("totalClientsNormaux", totalClientsNormaux);
    stats.put("totalClientsAffilies", totalClientsAffilies);
    stats.put("totalClientsActifs", totalClientsActifs);
    stats.put("ventesMois", ventesMois != null ? ventesMois : 0.0);
    stats.put("commissionsMois", commissionsMois != null ? commissionsMois : 0.0);
    
    return stats;
}
    
    public Map<String, Object> getAffiliateStats(Long affiliateId) {
        User affiliate = userRepository.findById(affiliateId)
            .orElseThrow(() -> new IllegalArgumentException("Affilié non trouvé"));
        
        if (affiliate.getRole() != RoleType.AFFILIATE) {
            throw new IllegalArgumentException("Cet utilisateur n'est pas un affilié");
        }
        
        ClientInfo clientInfo = clientInfoRepository.findFirstByUser(affiliate)
            .orElseThrow(() -> new IllegalArgumentException("Info affilié non trouvée"));
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("codeAffiliation", clientInfo.getCodeAffiliation());
        stats.put("tauxCommission", clientInfo.getTauxCommission());
        stats.put("commissionGeneree", clientInfo.getCommissionGeneree());
        stats.put("nombreVentesLiees", clientInfo.getNombreVentesLiees());
        stats.put("source", clientInfo.getSource());
        
        return stats;
    }

    // ========== PERMISSION VALIDATION ==========
    
    /**
     * Valide les règles de partage pour les permissions
     * @param owner Le propriétaire du client (peut être null pour la création)
     * @param target L'utilisateur cible pour le partage
     * @param isAffiliate Si le client est un affilié
     */
    private void validateSharingRules(User owner, User target, boolean isAffiliate) {
        // Si owner est null, c'est la création par ADMIN/SUPER_ADMIN
        if (owner != null) {
            // Règle 1: Ne peut partager qu'avec des utilisateurs sous sa hiérarchie
            if (!userService.isDescendant(owner.getId(), target.getId())) {
                log.warn("Tentative de partage avec un utilisateur hors hiérarchie: owner={}, target={}", 
                    owner.getId(), target.getId());
                throw new SecurityException("Vous ne pouvez partager qu'avec des utilisateurs sous votre hiérarchie");
            }
        }
        
        // Règle 2: Les COMMERCIAL ne peuvent pas voir les clients affiliés
        if (isAffiliate && target.getRole() == RoleType.COMMERCIAL) {
            log.warn("Tentative de partage d'un affilié avec un commercial: target={}", target.getId());
            throw new SecurityException("Les commerciaux ne peuvent pas accéder aux clients affiliés");
        }
        
        // Règle 3: Un ADMIN ne peut pas partager avec un SUPER_ADMIN
        if (owner != null && owner.getRole() == RoleType.ADMIN && target.getRole() == RoleType.SUPER_ADMIN) {
            log.warn("Tentative de partage avec SUPER_ADMIN par un ADMIN");
            throw new SecurityException("Un ADMIN ne peut pas partager avec un SUPER_ADMIN");
        }
        
        // Règle 4: Un RESPONSABLE_COMMERCIAL ne peut partager qu'avec des COMMERCIAL
        if (owner != null && owner.getRole() == RoleType.RESPONSABLE_COMMERCIAL && 
            target.getRole() != RoleType.COMMERCIAL) {
            log.warn("Tentative de partage par responsable commercial avec un non-commercial");
            throw new SecurityException("Un responsable commercial ne peut partager qu'avec des commerciaux");
        }
        
        log.debug("Validation des règles de partage réussie pour target={}", target.getId());
    }

    // ========== CONVERSION ==========
    
    private ClientDTO convertToDTO(User user, ClientInfo clientInfo) {
        ClientDTO dto = new ClientDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setNom(user.getNom());
        dto.setPrenom(user.getPrenom());
        dto.setTelephone(user.getTelephone());
        dto.setRole(user.getRole().name());
        dto.setIsActive(user.getIsActive());
        dto.setCreatedAt(user.getCreatedAt());
        
        dto.setBudgetEstime(clientInfo.getBudgetEstime());
        dto.setZoneRecherchee(clientInfo.getZoneRecherchee());
        dto.setNombreAchats(clientInfo.getNombreAchats());
        dto.setNombreLocations(clientInfo.getNombreLocations());
        dto.setNombreReservations(clientInfo.getNombreReservations());
        dto.setTotalAchats(clientInfo.getTotalAchats());
        
        if (clientInfo.getCreatedBy() != null) {
            dto.setCreatedBy(clientInfo.getCreatedBy().getId());
            dto.setCreatedByName(clientInfo.getCreatedBy().getFullName());
        }
        
        dto.setVisibilityType(clientInfo.getVisibilityType());
        dto.setAgencyAdminId(clientInfo.getAgencyAdminId());
        
        if (clientInfo.getCommercial() != null) {
            dto.setCommercialId(clientInfo.getCommercial().getId());
            dto.setCommercialNom(clientInfo.getCommercial().getNom());
            dto.setCommercialPrenom(clientInfo.getCommercial().getPrenom());
        }
        
        if (user.getRole() == RoleType.AFFILIATE) {
            dto.setCodeAffiliation(clientInfo.getCodeAffiliation());
            dto.setTauxCommission(clientInfo.getTauxCommission());
            dto.setSource(clientInfo.getSource());
            // Always derive affiliate stats from AffiliateTransaction (source of truth)
            // so the count is accurate even for historical data before the ClientInfo fix.
            long txCount = affiliateTransactionRepository.countByAffiliateId(user.getId());
            Double totalComm = affiliateTransactionRepository.getTotalCommissionsByAffiliateId(user.getId());
            dto.setNombreVentesLiees((int) txCount);
            dto.setCommissionGeneree(totalComm != null ? totalComm : 0.0);
        }
        
        return dto;
    }
}