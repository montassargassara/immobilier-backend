package com.immobilier.backend.service;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.entity.*;
import com.immobilier.backend.enums.PendingSaleApprovalStatus;
import com.immobilier.backend.enums.PropertyValidationStatus;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final AffiliateRegionRepository affiliateRegionRepository;
    private final ImageService imageService;
    private final Model3DService model3DService;
    private final VideoService videoService;
    private final UserRepository userRepository;
    private final PropertySharedAgencyRepository sharedAgencyRepository;
    private final PropertyVisibilityService visibilityService;
    private final PropertyWorkflowService workflowService;
    private final InterestRequestRepository interestRequestRepository;
    private final ClientInfoRepository clientInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final SaleValidationService saleValidationService;
    private final SaleValidationRequestRepository saleValidationRequestRepository;
    private final CommissionService commissionService;
    private final AffiliateCustomerRelationRepository affiliateCustomerRelationRepository;

    // ========== CREATE ==========
    
    @Transactional
    public PropertyDTO createProperty(@Valid CreatePropertyRequest request) {
        log.info("Création d'une nouvelle propriété: {}", request.getTitre());
        
        // Validate category and prices
        validateCategoryAndPrices(request);
        
        Property property = new Property();
        property.setTitre(request.getTitre());
        property.setDescription(request.getDescription());
        property.setType(request.getType());
        property.setPrixVente(request.getPrixVente());
        property.setPrixLocation(request.getPrixLocation());
        
        // Set default status based on category if not specified
        String initialStatus = request.getStatut();
        if (initialStatus == null) {
            initialStatus = "DISPONIBLE";
        }
        
        // Validate status is allowed for this category
        String category = request.getCategory();
        if (!Property.isStatusAllowedForCategory(category, initialStatus)) {
            throw new IllegalArgumentException(
                String.format("Le statut '%s' n'est pas autorisé pour une propriété en %s. " +
                              "Statuts autorisés: %s",
                              initialStatus, 
                              category.toLowerCase(),
                              Property.getAllowedStatusesForCategory(category))
            );
        }
        
        property.setStatut(initialStatus);
        property.setSurface(request.getSurface());
        property.setNbChambres(request.getNbChambres());
        property.setAdresse(request.getAdresse());
        property.setCountry(request.getCountry());
        property.setCity(request.getCity());
        property.setRegion(extractRegionFromAddress(request.getAdresse()));
        property.setLatitude(request.getLatitude());
        property.setLongitude(request.getLongitude());
        
        if (request.getCommissionPercentage() != null) {
            property.setCommissionPercentage(request.getCommissionPercentage());
        }
        if (request.getCommissionType() != null) {
            property.setCommissionType(request.getCommissionType());
        }
        if (request.getBasePriceForCommission() != null) {
            property.setBasePriceForCommission(request.getBasePriceForCommission());
        }
        
        property.setIsActive(true);
        property.setIsAffiliateEligible(
            request.getIsAffiliateEligible() != null && request.getIsAffiliateEligible());

        Property savedProperty = propertyRepository.save(property);
        log.info("Propriété sauvegardée avec ID: {}, Catégorie: {}, Statut: {}",
                 savedProperty.getId(), category, savedProperty.getStatut());

        return convertToFullDTO(savedProperty);
    }

    /**
     * Create a property and assign ownership + initial workflow state based on the
     * calling user's role. Role-based field stripping is enforced here:
     *
     *   COMMERCIAL → cannot set price or commission. status = PENDING_RESPONSABLE.
     *   RESPONSABLE_COMMERCIAL → may set price; cannot set commission. status = PENDING_ADMIN.
     *   ADMIN/SUPER_ADMIN → full fields, immediately APPROVED, commission locked.
     */
    @Transactional
    public PropertyDTO createPropertyForUser(@Valid CreatePropertyRequest request, User currentUser) {
        log.info("Création propriété par {} (role={})", currentUser.getEmail(), currentUser.getRole());

        RoleType role = currentUser.getRole();

        // ─── Role-based field stripping (server-side authoritative) ──────────
        if (role == RoleType.COMMERCIAL) {
            // COMMERCIAL cannot set price or commission. Force-clear before validation.
            request.setPrixVente(null);
            request.setPrixLocation(null);
            request.setCommissionPercentage(null);
            request.setBasePriceForCommission(null);
            request.setIsAffiliateEligible(false);
        } else if (role == RoleType.RESPONSABLE_COMMERCIAL) {
            // RESPONSABLE may set price but never commission.
            request.setCommissionPercentage(null);
            request.setBasePriceForCommission(null);
            request.setIsAffiliateEligible(false);
        }

        // Skip the "at least one price" check for COMMERCIAL — they don't set prices.
        // Their submission is a draft awaiting price from RESPONSABLE.
        if (role != RoleType.COMMERCIAL) {
            validateCategoryAndPrices(request);
        }

        Property property = new Property();

        if (role == RoleType.COMMERCIAL) {
            // Build a draft without price; bypass mapRequestToProperty's status check.
            property.setTitre(request.getTitre());
            property.setDescription(request.getDescription());
            property.setType(request.getType());
            property.setStatut("EN_ATTENTE");
            property.setSurface(request.getSurface());
            property.setNbChambres(request.getNbChambres());
            property.setNbSallesDeBain(request.getNbSallesDeBain());
            property.setGarage(Boolean.TRUE.equals(request.getGarage()));
            property.setPiscine(Boolean.TRUE.equals(request.getPiscine()));
            property.setJardin(Boolean.TRUE.equals(request.getJardin()));
            property.setMeuble(Boolean.TRUE.equals(request.getMeuble()));
            property.setEtage(request.getEtage());
            property.setParkingSpaces(request.getParkingSpaces());
            property.setAnneeConstruction(request.getAnneeConstruction());
            property.setProchePlage(Boolean.TRUE.equals(request.getProchePlage()));
            property.setProcheTransport(Boolean.TRUE.equals(request.getProcheTransport()));
            property.setSecurite(Boolean.TRUE.equals(request.getSecurite()));
            property.setClimatisation(Boolean.TRUE.equals(request.getClimatisation()));
            property.setAdresse(request.getAdresse());
            property.setCountry(request.getCountry());
            property.setCity(request.getCity());
            property.setRegion(extractRegionFromAddress(request.getAdresse()));
            property.setLatitude(request.getLatitude());
            property.setLongitude(request.getLongitude());
            // Stub price so the entity's @PrePersist category check passes — overwritten by RESPONSABLE.
            property.setPrixVente(1.0);
        } else {
            mapRequestToProperty(request, property);
        }

        // Ownership assignment
        property.setOwnerType(visibilityService.resolveOwnerType(currentUser));
        User propertyOwner = visibilityService.resolvePropertyOwner(currentUser);

        // SUPER_ADMIN may explicitly assign to an agency via agencyAdminId in request
        if (role == RoleType.SUPER_ADMIN && request.getAgencyAdminId() != null) {
            User assignedAdmin = userRepository.findById(request.getAgencyAdminId())
                    .orElseThrow(() -> new RuntimeException("Agence introuvable: " + request.getAgencyAdminId()));
            if (assignedAdmin.getRole() != RoleType.ADMIN) {
                throw new IllegalArgumentException("L'ID spécifié ne correspond pas à un admin d'agence");
            }
            property.setOwnerType("AGENCY_OWNED");
            property.setAgencyAdmin(assignedAdmin);
        } else {
            property.setAgencyAdmin(propertyOwner);
        }

        // Authoring + workflow state
        property.setCreatedBy(currentUser);
        property.setOwnerRole(role);
        PropertyValidationStatus initial = workflowService.initialStatusFor(role);
        property.setValidationStatus(initial);

        // Locks: ADMIN/SUPER_ADMIN approval implicitly locks both fields once set
        boolean isApprover = role == RoleType.ADMIN || role == RoleType.SUPER_ADMIN;
        property.setPriceLocked(isApprover);
        property.setCommissionLocked(isApprover && property.getCommissionPercentage() != null
                && property.getCommissionPercentage() > 0);

        property.setIsActive(true);
        property.setIsAffiliateEligible(
            request.getIsAffiliateEligible() != null && request.getIsAffiliateEligible());

        Property saved = propertyRepository.save(property);
        log.info("Propriété {} créée par {} (role={}) avec ownerType={}, validationStatus={}",
                saved.getId(), currentUser.getEmail(), role, saved.getOwnerType(), saved.getValidationStatus());

        // Notify upstream validators if not already approved
        workflowService.notifySubmission(saved, currentUser);

        return convertToFullDTO(saved);
    }

    // ========== VISIBILITY-AWARE READ METHODS ==========

    /**
     * Returns the property list filtered by what the current user is allowed to see.
     */
    public List<PropertyListDTO> getAllPropertiesListForUser(User currentUser) {
        List<Property> properties = visibilityService.getVisibleProperties(currentUser);
        List<PropertyListDTO> dtos = properties.stream().map(this::convertToListDTO).collect(Collectors.toList());
        enrichWithInterestCounts(dtos);
        enrichWithPendingValidations(dtos);
        return dtos;
    }

    private void enrichWithPendingValidations(List<PropertyListDTO> dtos) {
        if (dtos.isEmpty()) return;
        List<Long> ids = dtos.stream().map(PropertyListDTO::getId).collect(Collectors.toList());
        java.util.Set<Long> pending = saleValidationRequestRepository.findPropertyIdsWithPendingValidation(ids);
        dtos.forEach(dto -> dto.setHasPendingValidation(pending.contains(dto.getId())));
    }

    private void enrichWithInterestCounts(List<PropertyListDTO> dtos) {
        if (dtos.isEmpty()) return;
        List<Long> ids = dtos.stream().map(PropertyListDTO::getId).collect(Collectors.toList());
        List<Object[]> rows = interestRequestRepository.countByPropertyIds(ids);
        java.util.Map<Long, Integer> countMap = new java.util.HashMap<>();
        for (Object[] row : rows) {
            countMap.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        dtos.forEach(dto -> dto.setInterestCount(countMap.getOrDefault(dto.getId(), 0)));
    }

    /**
     * Returns all VENDU (sold) properties visible to the current user, newest first.
     */
    public List<PropertyListDTO> getSoldPropertiesForUser(User currentUser) {
        List<Property> all = visibilityService.getVisibleProperties(currentUser);
        return all.stream()
                .filter(p -> "VENDU".equals(p.getStatut()))
                .sorted((a, b) -> {
                    if (b.getUpdatedAt() == null && a.getUpdatedAt() == null) return 0;
                    if (b.getUpdatedAt() == null) return -1;
                    if (a.getUpdatedAt() == null) return 1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    /**
     * Returns all properties bought or rented by a specific user (via lead conversion or direct sale),
     * for display in the client CRM profile.
     */
    public List<PropertyListDTO> getPropertiesForBuyerUser(Long buyerUserId) {
        return propertyRepository.findByBuyerIdOrderByUpdatedAtDesc(buyerUserId).stream()
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    public List<PropertyListDTO> getRentedPropertiesForUser(User currentUser) {
        List<Property> all = visibilityService.getVisibleProperties(currentUser);
        return all.stream()
                .filter(p -> "LOUE".equals(p.getStatut()))
                .sorted((a, b) -> {
                    if (b.getUpdatedAt() == null && a.getUpdatedAt() == null) return 0;
                    if (b.getUpdatedAt() == null) return -1;
                    if (a.getUpdatedAt() == null) return 1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    public List<PropertyListDTO> getExpiredRentalsForUser(User currentUser) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Property> all = visibilityService.getVisibleProperties(currentUser);
        return all.stream()
                .filter(p -> "LOUE".equals(p.getStatut())
                        && p.getRentalEndDate() != null
                        && now.isAfter(p.getRentalEndDate()))
                .sorted((a, b) -> {
                    if (a.getRentalEndDate() == null) return 1;
                    if (b.getRentalEndDate() == null) return -1;
                    return a.getRentalEndDate().compareTo(b.getRentalEndDate());
                })
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    /**
     * SUPER_ADMIN may view and share AGENCY_OWNED properties, but must NEVER mutate them.
     * Strict separation between super-admin and agency ownership.
     *
     * Both signals are checked for defense-in-depth: legacy rows may have ownerType set
     * but no ownerRole (backfilled retroactively), while newer rows always have both.
     *
     * Throws with the "Accès refusé" prefix so PropertyController maps it to HTTP 403.
     */
    private void assertSuperAdminCanMutate(Property property, User currentUser) {
        if (currentUser.getRole() != RoleType.SUPER_ADMIN) return;

        boolean agencyOwnedByType = "AGENCY_OWNED".equals(property.getOwnerType());
        boolean agencyOwnedByRole = property.getOwnerRole() == RoleType.ADMIN
                || property.getOwnerRole() == RoleType.RESPONSABLE_COMMERCIAL
                || property.getOwnerRole() == RoleType.COMMERCIAL;
        boolean createdByAgencyUser = property.getCreatedBy() != null
                && property.getCreatedBy().getRole() != RoleType.SUPER_ADMIN;

        if (agencyOwnedByType || agencyOwnedByRole || (property.getOwnerType() == null && createdByAgencyUser)) {
            throw new RuntimeException(
                    "Accès refusé: le Super Admin ne peut pas modifier un bien appartenant à une agence");
        }
    }

    /**
     * Returns a single property after verifying the current user has access.
     */
    public PropertyDTO getPropertyByIdForUser(Long id, User currentUser) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée: " + id));

        if (!visibilityService.canAccess(currentUser, property)) {
            throw new RuntimeException("Accès refusé à la propriété " + id);
        }
        return convertToFullDTO(property);
    }

    /**
     * Update a property with role-based field stripping, ownership checks, and locks.
     *
     *   COMMERCIAL: only own properties, only when not yet APPROVED. Cannot edit price/commission.
     *               Modifications fire an audit notification to RESPONSABLE + ADMIN.
     *   RESPONSABLE_COMMERCIAL: any property in the agency. Cannot edit commission. May edit price
     *               only while priceLocked = false.
     *   ADMIN: any property in the agency. Cannot edit SUPER_ADMIN_OWNED properties.
     *   SUPER_ADMIN: any SUPER_ADMIN_OWNED property. Cannot edit AGENCY_OWNED ones.
     *
     * If commissionLocked = true, lower roles' attempted commission edits are silently dropped.
     */
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO updatePropertyForUser(Long id, @Valid UpdatePropertyRequest request, User currentUser) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée: " + id));

        if (!visibilityService.canAccess(currentUser, property)) {
            throw new RuntimeException("Accès refusé à la propriété " + id);
        }

        RoleType role = currentUser.getRole();
        boolean isCreator = property.getCreatedBy() != null
                && property.getCreatedBy().getId().longValue() == currentUser.getId().longValue();

        // ─── Role-based authorization ────────────────────────────────────────
        if (role == RoleType.COMMERCIAL) {
            if (!isCreator) {
                throw new RuntimeException("Accès refusé: un commercial ne peut modifier que ses propres biens");
            }
            if (property.getValidationStatus() == PropertyValidationStatus.APPROVED) {
                throw new RuntimeException("Ce bien est validé et verrouillé pour les commerciaux");
            }
        }
        assertSuperAdminCanMutate(property, currentUser);
        if (role == RoleType.ADMIN && "SUPER_ADMIN_OWNED".equals(property.getOwnerType())) {
            throw new RuntimeException("Accès refusé: une agence ne peut pas modifier un bien Super Admin");
        }
        if (role == RoleType.RESPONSABLE_COMMERCIAL && "SUPER_ADMIN_OWNED".equals(property.getOwnerType())) {
            throw new RuntimeException("Accès refusé: un Responsable ne peut pas modifier un bien Super Admin");
        }

        // ─── Field stripping based on locks + role ───────────────────────────
        if (role == RoleType.COMMERCIAL) {
            request.setPrixVente(null);
            request.setPrixLocation(null);
            request.setIsAffiliateEligible(null);
        }
        // RESPONSABLE and COMMERCIAL cannot set affiliate eligibility (commission via separate endpoint is also blocked)
        if (role == RoleType.RESPONSABLE_COMMERCIAL || role == RoleType.COMMERCIAL) {
            request.setIsAffiliateEligible(null);
        }
        if (role != RoleType.ADMIN && role != RoleType.SUPER_ADMIN) {
            // Only ADMIN/SUPER_ADMIN can ever change the affiliate flag and the
            // commission. RESPONSABLE_COMMERCIAL is an operational validator,
            // never a financial manager — commission edits are stripped here.
            request.setIsAffiliateEligible(null);
            request.setCommissionPercentage(null);
            request.setCommissionType(null);
        }
        if (Boolean.TRUE.equals(property.getPriceLocked())
                && role != RoleType.ADMIN && role != RoleType.SUPER_ADMIN) {
            request.setPrixVente(null);
            request.setPrixLocation(null);
        }

        PropertyDTO result = updateProperty(id, request);

        // Audit notification when COMMERCIAL modifies (no approval required per spec)
        if (role == RoleType.COMMERCIAL) {
            workflowService.notifyModification(property, currentUser);
        }
        return result;
    }

    /**
     * Soft-delete a property after verifying the current user has access.
     * SUPER_ADMIN cannot delete AGENCY_OWNED properties (strict ownership separation).
     */
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public void deletePropertyForUser(Long id, User currentUser) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée: " + id));

        if (!visibilityService.canAccess(currentUser, property)) {
            throw new RuntimeException("Accès refusé à la propriété " + id);
        }
        assertSuperAdminCanMutate(property, currentUser);
        deleteProperty(id);
    }

    // ========== SHARING (SUPER_ADMIN ONLY) ==========

    /**
     * Replaces the shared-agency list for a SUPER_ADMIN_OWNED property.
     * Passing an empty list removes all shares.
     */
    @Transactional
    public PropertyDTO sharePropertyWithAgencies(Long propertyId, List<Long> agencyAdminIds, User currentUser) {
        if (currentUser.getRole() != RoleType.SUPER_ADMIN) {
            throw new RuntimeException("Seul le Super Admin peut partager une propriété");
        }

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée: " + propertyId));

        if (!"SUPER_ADMIN_OWNED".equals(property.getOwnerType())) {
            throw new IllegalArgumentException(
                    "Seules les propriétés Super Admin peuvent être partagées avec des agences");
        }

        // Remove all existing shares first, then add the new set
        sharedAgencyRepository.deleteAllByPropertyId(propertyId);

        if (agencyAdminIds != null) {
            for (Long adminId : agencyAdminIds) {
                User admin = userRepository.findById(adminId)
                        .orElseThrow(() -> new RuntimeException("Admin introuvable: " + adminId));
                if (admin.getRole() != RoleType.ADMIN) {
                    throw new IllegalArgumentException("L'utilisateur " + adminId + " n'est pas un admin d'agence");
                }
                PropertySharedAgency share = new PropertySharedAgency();
                share.setProperty(property);
                share.setAgencyAdmin(admin);
                share.setSharedBy(currentUser);
                sharedAgencyRepository.save(share);
            }
        }

        log.info("Propriété {} partagée avec {} agences", propertyId,
                agencyAdminIds != null ? agencyAdminIds.size() : 0);
        return convertToFullDTO(property);
    }

    /**
     * Revokes sharing for a single agency admin on a property.
     */
    @Transactional
    public void revokePropertySharing(Long propertyId, Long agencyAdminId, User currentUser) {
        if (currentUser.getRole() != RoleType.SUPER_ADMIN) {
            throw new RuntimeException("Seul le Super Admin peut retirer un partage");
        }
        sharedAgencyRepository.deleteByPropertyIdAndAgencyAdminId(propertyId, agencyAdminId);
        log.info("Partage propriété {} / agence {} révoqué", propertyId, agencyAdminId);
    }

    // ========== DIRECT SALE / RENTAL (ADMIN / SUPER_ADMIN) ==========

    /**
     * Links a buyer to a property and sets its status to VENDU or LOUE directly
     * (without going through the CRM lead pipeline).
     * - Finds or creates a CLIENT user account for the buyer.
     * - Increments ClientInfo.nombreAchats / totalAchats.
     * - For LOUE: stores the rental contract dates on the property.
     */
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#propertyId")
    public PropertyListDTO processDirectSale(Long propertyId, DirectSaleRequest req, User currentUser) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée: " + propertyId));

        if (!visibilityService.canAccess(currentUser, property)) {
            throw new RuntimeException("Accès refusé à la propriété " + propertyId);
        }

        String targetStatus = req.getTargetStatus();
        if (!"VENDU".equals(targetStatus) && !"LOUE".equals(targetStatus)) {
            throw new IllegalArgumentException("targetStatus doit être VENDU ou LOUE");
        }

        // ── Cross-ownership check: if the requester doesn't own this property,
        //    create a validation request and put the property on hold. ────────
        if (!saleValidationService.isPropertyOwner(currentUser, property)) {
            saleValidationService.createForDirectSale(property, req, currentUser);
            return convertToListDTO(property); // statut is now EN_ATTENTE (cross-ownership validation pending)
        }

        // ── Resolve or create the buyer ─────────────────────────────────────
        User buyer = resolveBuyer(req, currentUser);

        // ── Update property ─────────────────────────────────────────────────
        property.setBuyer(buyer);
        property.setStatut(targetStatus);

        if ("VENDU".equals(targetStatus)) {
            property.setIsFinalized(true);
        } else {
            // LOUE — rental contract fields
            if (req.getRentalStartDate() == null || req.getRentalDurationMonths() == null
                    || req.getRentalDurationMonths() < 1) {
                throw new IllegalArgumentException(
                        "Pour une location, la date de début et la durée (≥ 1 mois) sont obligatoires");
            }
            LocalDateTime start = LocalDate.parse(req.getRentalStartDate()).atStartOfDay();
            LocalDateTime end   = start.plusMonths(req.getRentalDurationMonths());
            property.setRentalStartDate(start);
            property.setRentalEndDate(end);
            property.setRentalDurationMonths(req.getRentalDurationMonths());
        }

        Property saved = propertyRepository.save(property);

        // ── Update buyer stats ───────────────────────────────────────────────
        updateBuyerStats(buyer, property, targetStatus);

        // ── Record staff / agency commissions (never blocks the sale) ────────
        commissionService.recordForCompletedSale(
                saved, currentUser, "VENDU".equals(targetStatus) ? "SALE" : "RENT");

        log.info("Vente directe propriété {} → {} par {} (acheteur ID {})",
                propertyId, targetStatus, currentUser.getEmail(), buyer.getId());

        return convertToListDTO(saved);
    }

    /**
     * Finalises a previously-validated cross-ownership direct sale.
     * Called by {@link SaleValidationService} after the property owner approves.
     * The property statut was EN_ATTENTE during validation; this
     * method applies the real VENDU / LOUE change.
     */
    @Transactional
    public void completeValidatedDirectSale(Property property,
                                            DirectSaleRequest req,
                                            User reviewer) {
        String targetStatus = req.getTargetStatus();

        User buyer = resolveBuyer(req, reviewer);
        property.setBuyer(buyer);
        property.setStatut(targetStatus);

        if ("VENDU".equals(targetStatus)) {
            property.setIsFinalized(true);
        } else {
            // LOUE — rental contract
            if (req.getRentalStartDate() == null || req.getRentalDurationMonths() == null
                    || req.getRentalDurationMonths() < 1) {
                throw new IllegalArgumentException(
                        "Pour une location, la date de début et la durée (≥ 1 mois) sont obligatoires");
            }
            LocalDateTime start = LocalDate.parse(req.getRentalStartDate()).atStartOfDay();
            LocalDateTime end   = start.plusMonths(req.getRentalDurationMonths());
            property.setRentalStartDate(start);
            property.setRentalEndDate(end);
            property.setRentalDurationMonths(req.getRentalDurationMonths());
        }

        propertyRepository.save(property);
        updateBuyerStats(buyer, property, targetStatus);

        log.info("Validated direct sale completed — property {} → {} (reviewer {})",
                property.getId(), targetStatus, reviewer.getEmail());
    }

    private User resolveBuyer(DirectSaleRequest req, User currentUser) {
        if (req.getExistingClientId() != null) {
            return userRepository.findById(req.getExistingClientId())
                    .orElseThrow(() -> new IllegalArgumentException("Client introuvable: " + req.getExistingClientId()));
        }

        // Create a new lightweight buyer account
        if (req.getClientEmail() == null || req.getClientEmail().isBlank()) {
            throw new IllegalArgumentException("L'email du client est obligatoire pour créer un nouveau compte");
        }
        if (req.getClientNom() == null || req.getClientNom().isBlank()) {
            throw new IllegalArgumentException("Le nom du client est obligatoire");
        }

        // Reuse existing account if email already exists
        Optional<User> existing = userRepository.findByEmail(req.getClientEmail().trim().toLowerCase());
        if (existing.isPresent()) {
            return existing.get();
        }

        User newUser = new User();
        newUser.setNom(req.getClientNom().trim());
        newUser.setPrenom(req.getClientPrenom() != null ? req.getClientPrenom().trim() : "");
        newUser.setEmail(req.getClientEmail().trim().toLowerCase());
        String tel = req.getClientTelephone();
        newUser.setTelephone((tel != null && !tel.isBlank()) ? tel.trim() : null);
        newUser.setRole(RoleType.CLIENT);
        newUser.setIsActive(true);
        // Random secure password — the buyer will need to use "forgot password" to log in
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        User savedUser = userRepository.save(newUser);

        // Create ClientInfo record
        ClientInfo info = new ClientInfo();
        info.setUser(savedUser);
        info.setCreatedBy(currentUser);
        info.setVisibilityType("AGENCY_CLIENT");
        // Resolve agency admin id for the creator
        if (currentUser.getRole() == RoleType.ADMIN) {
            info.setAgencyAdminId(currentUser.getId());
        } else if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            info.setAgencyAdminId(null);
            info.setVisibilityType("PRIVATE_CLIENT");
        } else {
            // COMMERCIAL / RESPONSABLE — find their ADMIN ancestor
            userRepository.findTopAdminAncestor(currentUser.getId())
                    .ifPresent(admin -> info.setAgencyAdminId(admin.getId()));
        }
        clientInfoRepository.save(info);

        return savedUser;
    }

    private void updateBuyerStats(User buyer, Property property, String status) {
        boolean isVente = "VENDU".equals(status);
        double price = isVente
                ? (property.getPrixVente() != null ? property.getPrixVente() : 0.0)
                : (property.getPrixLocation() != null ? property.getPrixLocation() : 0.0);
        for (ClientInfo info : clientInfoRepository.findAllByUserId(buyer.getId())) {
            if (isVente) {
                info.setNombreAchats((info.getNombreAchats() == null ? 0 : info.getNombreAchats()) + 1);
            } else {
                info.setNombreLocations((info.getNombreLocations() == null ? 0 : info.getNombreLocations()) + 1);
            }
            info.setTotalAchats((info.getTotalAchats() == null ? 0.0 : info.getTotalAchats()) + price);
            clientInfoRepository.save(info);
        }
    }

    /**
     * Returns the list of agency admins this property is currently shared with,
     * and all available admins with their sharing status — for the share UI.
     */
    public List<AgencyAdminDTO> getAgenciesForPropertySharing(Long propertyId) {
        List<Long> sharedIds = sharedAgencyRepository.findAgencyAdminIdsByPropertyId(propertyId);
        return userRepository.findByRoleAndIsActiveTrue(RoleType.ADMIN).stream()
                .map(admin -> new AgencyAdminDTO(
                        admin.getId(),
                        admin.getFullName(),
                        admin.getEmail(),
                        sharedIds.contains(admin.getId())))
                .collect(Collectors.toList());
    }

    // ========== HELPER: map request fields to entity ==========

    private void mapRequestToProperty(CreatePropertyRequest request, Property property) {
        String category = request.getCategory();
        String initialStatus = request.getStatut() != null ? request.getStatut() : "DISPONIBLE";

        if (!Property.isStatusAllowedForCategory(category, initialStatus)) {
            throw new IllegalArgumentException(String.format(
                    "Le statut '%s' n'est pas autorisé pour une propriété en %s. Statuts autorisés: %s",
                    initialStatus, category.toLowerCase(), Property.getAllowedStatusesForCategory(category)));
        }

        property.setTitre(request.getTitre());
        property.setDescription(request.getDescription());
        property.setType(request.getType());
        property.setPrixVente(request.getPrixVente());
        property.setPrixLocation(request.getPrixLocation());
        property.setStatut(initialStatus);
        property.setSurface(request.getSurface());
        property.setNbChambres(request.getNbChambres());
        property.setNbSallesDeBain(request.getNbSallesDeBain());
        property.setGarage(Boolean.TRUE.equals(request.getGarage()));
        property.setPiscine(Boolean.TRUE.equals(request.getPiscine()));
        property.setJardin(Boolean.TRUE.equals(request.getJardin()));
        property.setMeuble(Boolean.TRUE.equals(request.getMeuble()));
        property.setEtage(request.getEtage());
        property.setParkingSpaces(request.getParkingSpaces());
        property.setAnneeConstruction(request.getAnneeConstruction());
        property.setProchePlage(Boolean.TRUE.equals(request.getProchePlage()));
        property.setProcheTransport(Boolean.TRUE.equals(request.getProcheTransport()));
        property.setSecurite(Boolean.TRUE.equals(request.getSecurite()));
        property.setClimatisation(Boolean.TRUE.equals(request.getClimatisation()));
        property.setAdresse(request.getAdresse());
        property.setCountry(request.getCountry());
        property.setCity(request.getCity());
        property.setRegion(extractRegionFromAddress(request.getAdresse()));
        property.setLatitude(request.getLatitude());
        property.setLongitude(request.getLongitude());

        if (request.getCommissionPercentage() != null) property.setCommissionPercentage(request.getCommissionPercentage());
        if (request.getCommissionType() != null) property.setCommissionType(request.getCommissionType());
        if (request.getBasePriceForCommission() != null) property.setBasePriceForCommission(request.getBasePriceForCommission());
    }

       private void validateCategoryAndPrices(CreatePropertyRequest request) {
        boolean hasSalePrice = request.getPrixVente() != null && request.getPrixVente() > 0;
        boolean hasRentalPrice = request.getPrixLocation() != null && request.getPrixLocation() > 0;
        
        if (!hasSalePrice && !hasRentalPrice) {
            throw new IllegalArgumentException(
                "Veuillez spécifier soit un prix de vente, soit un prix de location"
            );
        }
        
        if (hasSalePrice && hasRentalPrice) {
            throw new IllegalArgumentException(
                "Une propriété ne peut pas être à la fois en vente et en location. " +
                "Veuillez spécifier UN SEUL type de prix."
            );
        }
        
        if (hasSalePrice && request.getPrixVente() <= 0) {
            throw new IllegalArgumentException("Le prix de vente doit être supérieur à 0");
        }
        
        if (hasRentalPrice && request.getPrixLocation() <= 0) {
            throw new IllegalArgumentException("Le prix de location doit être supérieur à 0");
        }
    }

    private String extractRegionFromAddress(String address) {
        if (address == null) return null;
        String[] regions = {"Tunis", "Ariana", "Ben Arous", "Manouba", "Nabeul", 
                           "Zaghouan", "Bizerte", "Béja", "Jendouba", "Le Kef",
                           "Siliana", "Kairouan", "Kasserine", "Sidi Bouzid", "Sousse",
                           "Monastir", "Mahdia", "Sfax", "Gafsa", "Tozeur", "Kebili",
                           "Gabès", "Médenine", "Tataouine"};
        for (String region : regions) {
            if (address.toLowerCase().contains(region.toLowerCase())) {
                return region;
            }
        }
        return null;
    }

    // ========== LOCATION METHODS ==========
    
    public List<String> getAllCountries() {
        return propertyRepository.findAllCountries();
    }
    
    public List<String> getCitiesByCountry(String country) {
        return propertyRepository.findCitiesByCountry(country);
    }
    
    public List<PropertyWithCommissionDTO> getPropertiesByCountry(String country) {
        log.info("📋 Recherche de propriétés dans le pays: {}", country);
        List<Property> properties = propertyRepository.findByCountry(country);
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }
    
    public List<PropertyWithCommissionDTO> getPropertiesByCountryAndCity(String country, String city) {
        log.info("📋 Recherche de propriétés dans {}/{}", country, city);
        List<Property> properties = propertyRepository.findByCountryAndCity(country, city);
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }
    
    public List<PropertyWithCommissionDTO> getPropertiesByCity(String city) {
        log.info("📋 Recherche de propriétés dans la ville: {}", city);
        List<Property> properties = propertyRepository.findByCity(city);
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }
    
    public List<PropertyWithCommissionDTO> getPropertiesByArea(String area) {
        log.info("📋 Recherche de propriétés dans la zone: {}", area);
        List<Property> properties = propertyRepository.findByArea(area);
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }

    public List<PropertyWithCommissionDTO> getPropertiesByRegions(List<String> regions) {
        log.info("📋 Recherche de propriétés dans les régions: {}", regions);
        List<Property> properties = propertyRepository.findByRegions(regions);
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }

    public List<PropertyWithCommissionDTO> getPropertiesNearLocation(Double lat, Double lng, Double radiusKm) {
        log.info("📋 Recherche de propriétés dans un rayon de {} km autour de ({}, {})", radiusKm, lat, lng);
        List<Property> properties = propertyRepository.findPropertiesWithinRadius(lat, lng, radiusKm);
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }

    public List<PropertyWithCommissionDTO> getPropertiesForAffiliate(Long affiliateId) {
        log.info("📋 Récupération des propriétés pour l'affilié ID: {}", affiliateId);
        List<AffiliateRegion> affiliateRegions = affiliateRegionRepository.findByAffiliateIdAndIsActiveTrue(affiliateId);
        
        if (affiliateRegions.isEmpty()) {
            log.warn("Affilié ID {} n'a pas de régions configurées", affiliateId);
            return List.of();
        }
        
        List<String> regionNames = affiliateRegions.stream()
                .map(AffiliateRegion::getRegionName)
                .collect(Collectors.toList());
        
        List<Property> properties = propertyRepository.findByRegions(regionNames);
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }

    public List<PropertyWithCommissionDTO> getPropertiesByCommissionRange(Double minCommission, Double maxCommission) {
        log.info("📋 Recherche de propriétés avec commission entre {}% et {}%", minCommission, maxCommission);
        
        List<Property> properties;
        if (minCommission != null && maxCommission != null) {
            properties = propertyRepository.findByCommissionRange(minCommission, maxCommission);
        } else if (minCommission != null) {
            properties = propertyRepository.findByMinCommission(minCommission);
        } else {
            properties = propertyRepository.findPropertiesWithCommission();
        }
        
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }

    public List<PropertyWithCommissionDTO> filterProperties(String country, String city, String region, 
                                                            Double minPrice, Double maxPrice,
                                                            Double minCommission, String propertyType) {
        log.info("📋 Filtrage propriétés - Pays: {}, Ville: {}, Région: {}, Prix: {}-{}, Commission min: {}%, Type: {}", 
                 country, city, region, minPrice, maxPrice, minCommission, propertyType);
        
        List<Property> properties = propertyRepository.findWithFilters(
            country, city, region, minPrice, maxPrice, minCommission, propertyType);
        
        return properties.stream().map(this::convertToCommissionDTO).collect(Collectors.toList());
    }

    /**
     * Update commission. ADMIN/SUPER_ADMIN only — caller authorization is enforced
     * in PropertyController via @PreAuthorize. After ADMIN sets a non-zero commission,
     * commissionLocked flips to true so lower roles can never overwrite it.
     */
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO updatePropertyCommission(Long id, UpdateCommissionRequest request, User currentUser) {
        log.info("Mise à jour de la commission pour la propriété ID: {} par {}", id, currentUser.getEmail());

        if (currentUser.getRole() != RoleType.ADMIN && currentUser.getRole() != RoleType.SUPER_ADMIN) {
            throw new RuntimeException("Seul un Admin ou Super Admin peut définir la commission");
        }

        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));

        assertSuperAdminCanMutate(property, currentUser);

        if (request.getCommissionPercentage() != null) {
            property.setCommissionPercentage(request.getCommissionPercentage());
        }
        if (request.getCommissionType() != null) {
            property.setCommissionType(request.getCommissionType());
        }
        if (request.getBasePriceForCommission() != null) {
            property.setBasePriceForCommission(request.getBasePriceForCommission());
        }
        if (property.getCommissionPercentage() != null && property.getCommissionPercentage() > 0) {
            property.setCommissionLocked(true);
        }

        Property updatedProperty = propertyRepository.save(property);
        log.info("Commission mise à jour pour propriété ID {}: {}% (locked={})",
                 id, updatedProperty.getCommissionPercentage(), updatedProperty.getCommissionLocked());

        return convertToFullDTO(updatedProperty);
    }

    @Transactional
    public int updateCommissionByRegion(String region, Double newCommissionPercentage) {
        log.info("Mise à jour en masse de la commission pour la région {} à {}%", region, newCommissionPercentage);
        
        List<Property> properties = propertyRepository.findByRegion(region);
        int updatedCount = 0;
        
        for (Property property : properties) {
            property.setCommissionPercentage(newCommissionPercentage);
            propertyRepository.save(property);
            updatedCount++;
        }
        
        log.info("{} propriétés mises à jour dans la région {}", updatedCount, region);
        return updatedCount;
    }

    private PropertyWithCommissionDTO convertToCommissionDTO(Property property) {
        PropertyWithCommissionDTO dto = new PropertyWithCommissionDTO();
        
        dto.setId(property.getId());
        dto.setTitre(property.getTitre());
        dto.setDescription(property.getDescription());
        dto.setType(property.getType());
        dto.setPrixVente(property.getPrixVente());
        dto.setPrixLocation(property.getPrixLocation());
        dto.setStatut(property.getStatut());
        dto.setSurface(property.getSurface());
        dto.setNbChambres(property.getNbChambres());
        dto.setAdresse(property.getAdresse());
        dto.setCountry(property.getCountry());
        dto.setCity(property.getCity());
        dto.setRegion(property.getRegion());
        dto.setLatitude(property.getLatitude());
        dto.setLongitude(property.getLongitude());
        dto.setIsActive(property.getIsActive());
        dto.setCreatedAt(property.getCreatedAt());
        dto.setUpdatedAt(property.getUpdatedAt());
        
        // Commission information
        dto.setCommissionPercentage(property.getCommissionPercentage());
        dto.setCommissionType(property.getCommissionType());
        dto.setCommissionAmount(property.calculateCommissionAmount());
        dto.setCommissionDisplay(property.getCommissionDisplay());
        dto.setBasePriceForCommission(property.getBasePriceForCommission());
        
        // Display price for commission calculation
        Double priceForCommission = property.getPriceForCommission();
        if (priceForCommission != null) {
            dto.setPriceForCommissionDisplay(String.format("%,.0f TND", priceForCommission));
        }
        
        // Image principale
        if (property.getMainImageId() != null) {
            dto.setHasMainImage(true);
            dto.setMainImageUrl("/api/images/public/" + property.getMainImageId());
            
            try {
                ImageDTO imageInfo = imageService.getImageInfoById(property.getMainImageId());
                dto.setMainImageName(imageInfo.getFileName());
                dto.setMainImageType(imageInfo.getFileType());
                dto.setMainImageSize(imageInfo.getFileSize());
            } catch (Exception e) {
                log.warn("Could not fetch image info for ID: {}", property.getMainImageId());
            }
        } else {
            dto.setHasMainImage(false);
        }
        
        // Modèle 3D
        if (property.getMainModel3dId() != null) {
            dto.setHasModel3d(true);
            dto.setModel3dUrl("/api/models/public/" + property.getMainModel3dId());
            
            try {
                Model3DDTO modelInfo = model3DService.getModel3DInfoById(property.getMainModel3dId());
                dto.setModel3dName(modelInfo.getFileName());
                dto.setModel3dType(modelInfo.getFileType());
                dto.setModel3dSize(modelInfo.getFileSize());
            } catch (Exception e) {
                log.warn("Could not fetch model info for ID: {}", property.getMainModel3dId());
            }
        } else {
            dto.setHasModel3d(false);
        }
        
        return dto;
    }

    // ========== READ METHODS ==========

    public List<PropertyListDTO> getAllPropertiesList() {
        log.info("📋 Récupération de toutes les propriétés (version liste)");
        long start = System.currentTimeMillis();
        
        List<Property> properties = propertyRepository.findByIsActiveTrue();
        List<PropertyListDTO> dtos = properties.stream()
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - start;
        log.info("✅ {} propriétés récupérées en {} ms", dtos.size(), duration);
        
        return dtos;
    }

    @Cacheable(value = "properties", key = "'all'")
    public List<PropertyDTO> getAllProperties() {
        log.info("📋 Récupération de toutes les propriétés (version complète)");
        long start = System.currentTimeMillis();
        
        List<Property> properties = propertyRepository.findByIsActiveTrue();
        List<PropertyDTO> result = properties.stream()
                .map(this::convertToFullDTO)
                .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - start;
        log.info("✅ {} propriétés complètes récupérées en {} ms", result.size(), duration);
        
        return result;
    }

    @Cacheable(value = "properties", key = "'active'")
    public List<PropertyDTO> getActiveProperties() {
        return propertyRepository.findByIsActiveTrue().stream()
                .map(this::convertToFullDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "property", key = "#id")
    public PropertyDTO getPropertyById(Long id) {
        long start = System.currentTimeMillis();
        log.info("📋 Récupération de la propriété ID: {}", id);
        
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));
        
        PropertyDTO dto = convertToFullDTO(property);
        long duration = System.currentTimeMillis() - start;
        log.info("✅ Propriété ID {} convertie en {} ms", id, duration);
        return dto;
    }

    public PropertyListDTO getPropertyByIdLight(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));
        return convertToListDTO(property);
    }

    public List<PropertyMediaDTO> getPropertyMediaLight(Long propertyId) {
        List<PropertyMediaDTO> allMedia = new ArrayList<>();
        
        List<ImageDTO> images = imageService.getImagesInfoByPropertyId(propertyId);
        allMedia.addAll(images.stream().map(this::convertImageToMediaDTO).collect(Collectors.toList()));
        
        List<VideoDTO> videos = videoService.getVideosInfoByPropertyId(propertyId);
        allMedia.addAll(videos.stream().map(this::convertVideoToMediaDTO).collect(Collectors.toList()));
        
        Model3DDTO model = model3DService.getModel3DInfoByPropertyId(propertyId);
        if (model != null) {
            allMedia.add(convertModelToMediaDTO(model));
        }
        
        return allMedia;
    }

    public List<PropertyListDTO> getRecentPropertiesLight(int limit) {
        log.info("📋 Récupération LIGHT des {} propriétés les plus récentes", limit);
        
        List<Property> properties = propertyRepository.findRecentProperties(limit);
        
        return properties.stream()
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    // ========== UPDATE ==========
    
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO updateProperty(Long id, @Valid UpdatePropertyRequest request) {
        log.info("Mise à jour de la propriété ID: {}", id);
        
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));

        String oldCategory = property.getCategory();
        
        // Update prices
        if (request.getPrixVente() != null) property.setPrixVente(request.getPrixVente());
        if (request.getPrixLocation() != null) property.setPrixLocation(request.getPrixLocation());
        
        // Get new category
        String newCategory = request.getCategory();
        if (newCategory == null && (request.getPrixVente() != null || request.getPrixLocation() != null)) {
            newCategory = property.getCategory();
        }
        
        // If category changed, validate and adjust status if needed
        if (newCategory != null && !newCategory.equals(oldCategory)) {
            log.info("Catégorie changée de {} à {}", oldCategory, newCategory);
            
            // Clear the inappropriate price
            if ("VENTE".equals(newCategory)) {
                property.setPrixLocation(null);
                // Ensure status is valid for sale
                String currentStatus = request.getStatut() != null ? request.getStatut() : property.getStatut();
                if (!Property.isStatusAllowedForCategory("VENTE", currentStatus)) {
                    property.setStatut("DISPONIBLE");
                    log.info("Statut réinitialisé à DISPONIBLE car {} n'est pas valide pour la vente", currentStatus);
                }
            } else if ("LOCATION".equals(newCategory)) {
                property.setPrixVente(null);
                // Ensure status is valid for rental
                String currentStatus = request.getStatut() != null ? request.getStatut() : property.getStatut();
                if (!Property.isStatusAllowedForCategory("LOCATION", currentStatus)) {
                    property.setStatut("DISPONIBLE");
                    log.info("Statut réinitialisé à DISPONIBLE car {} n'est pas valide pour la location", currentStatus);
                }
            }
        }
        
        // Update other fields
        if (request.getTitre() != null) property.setTitre(request.getTitre());
        if (request.getDescription() != null) property.setDescription(request.getDescription());
        if (request.getType() != null) property.setType(request.getType());
        if (request.getStatut() != null) {
            // Enforce business-rule locks before the category check
            validateStatusTransition(property, request.getStatut());

            // Validate status is allowed for current category
            String currentCategory = property.getCategory();
            if (!Property.isStatusAllowedForCategory(currentCategory, request.getStatut())) {
                throw new IllegalArgumentException(
                    String.format("Le statut '%s' n'est pas autorisé pour une propriété en %s. " +
                                  "Statuts autorisés: %s",
                                  request.getStatut(),
                                  currentCategory.toLowerCase(),
                                  Property.getAllowedStatusesForCategory(currentCategory))
                );
            }
            String incomingStatus = request.getStatut();
            // When transitioning TO LOUE: stamp start date and compute end date
            if ("LOUE".equals(incomingStatus) && !"LOUE".equals(property.getStatut())) {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                property.setRentalStartDate(now);
                Integer months = request.getRentalDurationMonths() != null
                        ? request.getRentalDurationMonths()
                        : property.getRentalDurationMonths();
                if (months != null && months > 0) {
                    property.setRentalEndDate(now.plusMonths(months));
                }
            }
            // When transitioning TO VENDU: permanently finalize
            // Also covers existing rows where isFinalized was null (legacy data)
            if ("VENDU".equals(incomingStatus) || Boolean.TRUE.equals(property.getIsFinalized())) {
                property.setIsFinalized(true);
            }
            property.setStatut(incomingStatus);
        }
        if (request.getRentalDurationMonths() != null) {
            property.setRentalDurationMonths(request.getRentalDurationMonths());
            // Recompute end date if already LOUE and duration changed
            if ("LOUE".equals(property.getStatut()) && property.getRentalStartDate() != null) {
                property.setRentalEndDate(
                    property.getRentalStartDate().plusMonths(request.getRentalDurationMonths())
                );
            }
        }
        if (request.getSurface() != null) property.setSurface(request.getSurface());
        if (request.getNbChambres() != null) property.setNbChambres(request.getNbChambres());
        if (request.getNbSallesDeBain() != null) property.setNbSallesDeBain(request.getNbSallesDeBain());
        if (request.getGarage() != null) property.setGarage(request.getGarage());
        if (request.getPiscine() != null) property.setPiscine(request.getPiscine());
        if (request.getJardin() != null) property.setJardin(request.getJardin());
        if (request.getMeuble() != null) property.setMeuble(request.getMeuble());
        if (request.getEtage() != null) property.setEtage(request.getEtage());
        if (request.getParkingSpaces() != null) property.setParkingSpaces(request.getParkingSpaces());
        if (request.getAnneeConstruction() != null) property.setAnneeConstruction(request.getAnneeConstruction());
        if (request.getProchePlage() != null) property.setProchePlage(request.getProchePlage());
        if (request.getProcheTransport() != null) property.setProcheTransport(request.getProcheTransport());
        if (request.getSecurite() != null) property.setSecurite(request.getSecurite());
        if (request.getClimatisation() != null) property.setClimatisation(request.getClimatisation());
        if (request.getAdresse() != null) property.setAdresse(request.getAdresse());
        if (request.getCountry() != null) property.setCountry(request.getCountry());
        if (request.getCity() != null) property.setCity(request.getCity());
        if (request.getLatitude() != null) property.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) property.setLongitude(request.getLongitude());
        if (request.getIsActive() != null) property.setIsActive(request.getIsActive());

        // Commission — editable until the property is actually sold/rented.
        // The lock is the SALE state (VENDU/LOUE/finalized), NOT the listing
        // validation state. Role gating is enforced upstream in
        // updatePropertyForUser (only ADMIN/SUPER_ADMIN reach here with these set).
        boolean saleClosed = "VENDU".equals(property.getStatut())
                || "LOUE".equals(property.getStatut())
                || Boolean.TRUE.equals(property.getIsFinalized());
        if (!saleClosed) {
            if (request.getCommissionPercentage() != null) {
                property.setCommissionPercentage(request.getCommissionPercentage());
            }
            if (request.getCommissionType() != null) {
                property.setCommissionType(request.getCommissionType());
            }
        }

        // Defense-in-depth: never allow this generic update path to flip the affiliate
        // flag once commission is locked unless the caller already passed role checks.
        if (request.getIsAffiliateEligible() != null) {
            if (Boolean.TRUE.equals(property.getCommissionLocked())
                    && (property.getCommissionPercentage() == null || property.getCommissionPercentage() == 0)) {
                // Skip — affiliate eligibility requires a real commission
            } else {
                property.setIsAffiliateEligible(request.getIsAffiliateEligible());
            }
        }

        Property updatedProperty = propertyRepository.save(property);
        log.info("Propriété ID {} mise à jour - Catégorie: {}, Statut: {}", 
                 id, updatedProperty.getCategory(), updatedProperty.getStatut());
        
        return convertToFullDTO(updatedProperty);
    }

    /**
     * Update status with role-aware sale routing.
     * When the new status is VENDU/LOUE, fire the upward notifications:
     *   - COMMERCIAL/RESPONSABLE → notify ADMIN
     *   - SUPER_ADMIN_OWNED sold by agency → notify SUPER_ADMIN
     *   - Sold without commission → COMMISSION_REQUIRED to ADMIN
     */
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO updatePropertyStatusForUser(Long id, String newStatus,
                                                    Integer rentalDurationMonths, User currentUser) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));

        if (!visibilityService.canAccess(currentUser, property)) {
            throw new RuntimeException("Accès refusé à la propriété " + id);
        }

        // Enforce business locks: VENDU is permanent, LOUE is locked until rental ends
        validateStatusTransition(property, newStatus);

        String category = property.getCategory();
        if (!Property.isStatusAllowedForCategory(category, newStatus)) {
            throw new IllegalArgumentException(
                String.format("Le statut '%s' n'est pas autorisé pour une propriété en %s.",
                              newStatus, category.toLowerCase()));
        }

        // Route through workflow whenever cross-ownership + significant status
        if (workflowService.requiresSaleApproval(property, currentUser, newStatus)) {
            workflowService.requestSaleApproval(property, currentUser, newStatus);
            return convertToFullDTO(property); // statut unchanged; pendingSaleApproval = PENDING
        }

        // Direct apply
        boolean isSaleFinal = "VENDU".equals(newStatus) || "LOUE".equals(newStatus);
        property.setStatut(newStatus);

        // VENDU: permanently finalize
        if ("VENDU".equals(newStatus)) {
            property.setIsFinalized(true);
        }

        // LOUE: stamp rental period
        if ("LOUE".equals(newStatus)) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            property.setRentalStartDate(now);
            Integer months = rentalDurationMonths != null ? rentalDurationMonths
                    : property.getRentalDurationMonths();
            if (months != null && months > 0) {
                property.setRentalDurationMonths(months);
                property.setRentalEndDate(now.plusMonths(months));
            }
        }

        if (isSaleFinal) {
            property.setPendingSaleApproval(null);
            property.setPendingSaleStatut(null);
            property.setPendingSaleRequestedBy(null);
            property.setPendingSaleApproverRole(null);
        }
        Property saved = propertyRepository.save(property);
        if (isSaleFinal) {
            workflowService.notifySale(property, currentUser, newStatus);
        }
        return convertToFullDTO(saved);
    }

    /**
     * ADMIN or SUPER_ADMIN approves a pending sale request on a property.
     */
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO approvePendingSaleForUser(Long id, User approver) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));

        if (property.getPendingSaleApproval() != PendingSaleApprovalStatus.PENDING) {
            throw new RuntimeException("Aucune vente en attente pour cette propriété");
        }
        assertCallerIsExpectedApprover(property, approver);
        workflowService.approveSaleRequest(property, approver);
        return convertToFullDTO(property);
    }

    /**
     * ADMIN or SUPER_ADMIN rejects a pending sale request on a property.
     */
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO rejectPendingSaleForUser(Long id, String reason, User approver) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));

        if (property.getPendingSaleApproval() != PendingSaleApprovalStatus.PENDING) {
            throw new RuntimeException("Aucune vente en attente pour cette propriété");
        }
        assertCallerIsExpectedApprover(property, approver);
        workflowService.rejectSaleRequest(property, approver, reason);
        return convertToFullDTO(property);
    }

    private void assertCallerIsExpectedApprover(Property property, User caller) {
        RoleType expected = property.getPendingSaleApproverRole();
        RoleType actual = caller.getRole();
        if (expected == null) return; // legacy / no-role set — allow ADMIN/SUPER_ADMIN
        if (actual != expected) {
            throw new RuntimeException("Accès refusé: cette demande doit être approuvée par un "
                    + expected.name() + ", pas par un " + actual.name());
        }
    }

        @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO updatePropertyStatus(Long id, String newStatus) {
        log.info("Mise à jour du statut de la propriété ID: {} vers {}", id, newStatus);
        
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));
        
        String category = property.getCategory();
        
        // Validate status is allowed for this category
        if (!Property.isStatusAllowedForCategory(category, newStatus)) {
            throw new IllegalArgumentException(
                String.format("Le statut '%s' n'est pas autorisé pour une propriété en %s. " +
                              "Statuts autorisés: %s",
                              newStatus,
                              category.toLowerCase(),
                              Property.getAllowedStatusesForCategory(category))
            );
        }
        
        property.setStatut(newStatus);
        Property updatedProperty = propertyRepository.save(property);
        
        log.info("Statut de la propriété ID {} mis à jour: {}", id, newStatus);
        
        return convertToFullDTO(updatedProperty);
    }

    // ========== DELETE ==========
    
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public void deleteProperty(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));
        
        imageService.deleteAllImagesByPropertyId(id);
        videoService.deleteAllVideosByPropertyId(id);
        model3DService.deleteModel3DByPropertyId(id);
        
        property.setIsActive(false);
        propertyRepository.save(property);
        
        log.info("Propriété ID {} désactivée avec tous ses médias", id);
    }

    // ========== UPLOAD DE FICHIERS ==========
    
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#propertyId")
    public PropertyDTO uploadMainImage(Long propertyId, MultipartFile file) throws IOException {
        log.info("Upload d'image principale pour la propriété ID: {}", propertyId);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + propertyId));
        
        ImageUploadRequest request = new ImageUploadRequest();
        request.setPrimary(true);
        request.setTitle(property.getTitre());
        request.setAltText("Image principale de " + property.getTitre());
        
        ImageDTO uploadedImage = imageService.uploadImage(propertyId, file, request);
        
        property.setMainImageId(uploadedImage.getId());
        propertyRepository.save(property);
        
        log.info("Image principale uploadée avec succès pour propriété ID: {}", propertyId);
        
        return convertToFullDTO(property);
    }
    
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#propertyId")
    public PropertyDTO uploadModel3d(Long propertyId, MultipartFile file) throws IOException {
        log.info("Upload de modèle 3D pour la propriété ID: {}", propertyId);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + propertyId));
        
        Model3DDTO uploadedModel = model3DService.uploadModel3D(propertyId, file, 
            "Modèle 3D de " + property.getTitre());
        
        property.setMainModel3dId(uploadedModel.getId());
        propertyRepository.save(property);
        
        log.info("Modèle 3D uploadé avec succès pour propriété ID: {}", propertyId);
        
        return convertToFullDTO(property);
    }
    
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#propertyId")
    public PropertyMediaDTO uploadMedia(Long propertyId, MultipartFile file, String type, 
                                    Integer sortOrder, Boolean isPrimary) throws IOException {
        log.info("Upload de média pour la propriété ID: {}, type: {}", propertyId, type);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + propertyId));
        
        PropertyMediaDTO result = null;
        
        if ("IMAGE".equalsIgnoreCase(type)) {
            ImageUploadRequest request = new ImageUploadRequest();
            request.setPrimary(isPrimary != null && isPrimary);
            request.setTitle(property.getTitre());
            
            ImageDTO image = imageService.uploadImage(propertyId, file, request);
            result = convertImageToMediaDTO(image);
            
            if (isPrimary != null && isPrimary) {
                property.setMainImageId(image.getId());
                propertyRepository.save(property);
            }
            
        } else if ("VIDEO".equalsIgnoreCase(type)) {
            VideoUploadRequest request = new VideoUploadRequest();
            request.setPrimary(isPrimary != null && isPrimary);
            request.setTitle(property.getTitre());
            
            VideoDTO video = videoService.uploadVideo(propertyId, file, request);
            result = convertVideoToMediaDTO(video);
            
            if (isPrimary != null && isPrimary) {
                property.setMainVideoId(video.getId());
                propertyRepository.save(property);
            }
            
        } else if ("MODEL_3D".equalsIgnoreCase(type)) {
            Model3DDTO model = model3DService.uploadModel3D(propertyId, file, 
                "Modèle 3D de " + property.getTitre());
            result = convertModelToMediaDTO(model);
            
            property.setMainModel3dId(model.getId());
            propertyRepository.save(property);
        }
        
        log.info("Média uploadé avec succès pour propriété ID: {}", propertyId);
        return result;
    }
    
    public byte[] getMediaFile(Long mediaId, String mediaType) {
        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            return imageService.getImageData(mediaId);
        } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
            return videoService.getVideoData(mediaId);
        } else if ("MODEL_3D".equalsIgnoreCase(mediaType)) {
            return model3DService.getModel3DData(mediaId);
        }
        throw new RuntimeException("Type de média non supporté: " + mediaType);
    }
    
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#propertyId")
    public void deleteMedia(Long mediaId, String mediaType, Long propertyId) {
        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            imageService.deleteImage(mediaId, propertyId);
        } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
            videoService.deleteVideo(mediaId, propertyId);
        } else if ("MODEL_3D".equalsIgnoreCase(mediaType)) {
            model3DService.deleteModel3D(mediaId, propertyId);
        }
        log.info("Média ID {} supprimé", mediaId);
    }
    
    public byte[] getMainImage(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + propertyId));
        
        if (property.getMainImageId() == null) {
            throw new RuntimeException("Image non trouvée");
        }
        
        return imageService.getImageData(property.getMainImageId());
    }
    
    public byte[] getModel3d(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + propertyId));
        
        if (property.getMainModel3dId() == null) {
            throw new RuntimeException("Modèle 3D non trouvé");
        }
        
        return model3DService.getModel3DData(property.getMainModel3dId());
    }
    
    /**
     * Workflow validation. The exact transition depends on the property's current state:
     *   PENDING_RESPONSABLE → PENDING_ADMIN  (caller must be RESPONSABLE_COMMERCIAL+)
     *   PENDING_ADMIN       → APPROVED       (caller must be ADMIN+; commission must be set)
     *   REJECTED            → re-validation works the same as the original state
     */
    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO validateProperty(Long id, User currentUser, Double commissionOverride) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));

        if (!visibilityService.canAccess(currentUser, property)) {
            throw new RuntimeException("Accès refusé à la propriété " + id);
        }
        assertSuperAdminCanMutate(property, currentUser);

        PropertyValidationStatus current = property.getValidationStatus();
        if (current == null) current = PropertyValidationStatus.APPROVED;

        if (current == PropertyValidationStatus.PENDING_RESPONSABLE) {
            workflowService.validateByResponsable(property, currentUser);
        } else if (current == PropertyValidationStatus.PENDING_ADMIN
                || current == PropertyValidationStatus.REJECTED) {
            // The approving ADMIN sets the commission at validation time —
            // there is no automatic default. Persist it before the check.
            if ("VENTE".equals(property.getCategory())
                    && commissionOverride != null && commissionOverride > 0) {
                property.setCommissionPercentage(commissionOverride);
            }
            if ("VENTE".equals(property.getCategory())
                    && (property.getCommissionPercentage() == null
                        || property.getCommissionPercentage() <= 0)) {
                throw new RuntimeException(
                        "La commission doit être définie avant l'approbation finale");
            }
            workflowService.validateByAdmin(property, currentUser);
        }
        return convertToFullDTO(propertyRepository.findById(id).orElseThrow());
    }

    @Transactional
    @CacheEvict(value = {"properties", "property"}, key = "#id")
    public PropertyDTO rejectProperty(Long id, String reason, User currentUser) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));

        if (!visibilityService.canAccess(currentUser, property)) {
            throw new RuntimeException("Accès refusé à la propriété " + id);
        }
        assertSuperAdminCanMutate(property, currentUser);
        workflowService.reject(property, currentUser, reason);
        return convertToFullDTO(propertyRepository.findById(id).orElseThrow());
    }

    // ========== STATUS TRANSITION VALIDATION ==========

    /**
     * Enforces business rules that block manual status changes.
     * Called before every status change in updateProperty().
     * VENDU   → always final, no edits.
     * EN_ATTENTE + isReservedByAffiliate → managed by affiliate workflow.
     * LOUE    → locked until rentalStartDate + rentalDurationMonths has passed.
     */
    private void validateStatusTransition(Property property, String newStatut) {
        String current = property.getStatut();
        if (current == null || newStatut == null || newStatut.equals(current)) return;

        if (Boolean.TRUE.equals(property.getIsFinalized()) || "VENDU".equals(current)) {
            throw new IllegalStateException(
                "Ce bien est définitivement vendu. Aucune modification n'est possible."
            );
        }
        if ("EN_ATTENTE".equals(current) && Boolean.TRUE.equals(property.getIsReservedByAffiliate())) {
            throw new IllegalStateException(
                "Ce bien est en cours de transaction via le réseau affilié. " +
                "Le statut sera mis à jour automatiquement à la finalisation de l'offre."
            );
        }
        if ("LOUE".equals(current) && property.getRentalEndDate() != null) {
            if (java.time.LocalDateTime.now().isBefore(property.getRentalEndDate())) {
                throw new IllegalStateException(
                    "Ce bien est loué jusqu'au " + property.getRentalEndDate().toLocalDate() +
                    ". Modification du statut bloquée jusqu'à cette date."
                );
            }
        }
    }

    // ========== MÉTHODES DE CONVERSION ==========
    
    private PropertyListDTO convertToListDTO(Property property) {
        PropertyListDTO dto = new PropertyListDTO();
        
        dto.setId(property.getId());
        dto.setTitre(property.getTitre());
        dto.setDescription(property.getDescription());
        dto.setType(property.getType());
        dto.setPrixVente(property.getPrixVente());
        dto.setPrixLocation(property.getPrixLocation());
        dto.setStatut(property.getStatut());
        dto.setSurface(property.getSurface());
        dto.setNbChambres(property.getNbChambres());
        dto.setNbSallesDeBain(property.getNbSallesDeBain());
        dto.setGarage(Boolean.TRUE.equals(property.getGarage()));
        dto.setPiscine(Boolean.TRUE.equals(property.getPiscine()));
        dto.setJardin(Boolean.TRUE.equals(property.getJardin()));
        dto.setMeuble(Boolean.TRUE.equals(property.getMeuble()));
        dto.setEtage(property.getEtage());
        dto.setParkingSpaces(property.getParkingSpaces());
        dto.setAnneeConstruction(property.getAnneeConstruction());
        dto.setProchePlage(Boolean.TRUE.equals(property.getProchePlage()));
        dto.setProcheTransport(Boolean.TRUE.equals(property.getProcheTransport()));
        dto.setSecurite(Boolean.TRUE.equals(property.getSecurite()));
        dto.setClimatisation(Boolean.TRUE.equals(property.getClimatisation()));
        dto.setAdresse(property.getAdresse());
        dto.setCountry(property.getCountry());
        dto.setCity(property.getCity());
        dto.setLatitude(property.getLatitude());
        dto.setLongitude(property.getLongitude());
        dto.setIsActive(property.getIsActive());
        dto.setCreatedAt(property.getCreatedAt());
        dto.setUpdatedAt(property.getUpdatedAt());

        // Ownership
        dto.setOwnerType(property.getOwnerType());
        dto.setIsAffiliateEligible(property.getIsAffiliateEligible());
        if (property.getAgencyAdmin() != null) {
            dto.setAgencyAdminId(property.getAgencyAdmin().getId());
            dto.setAgencyAdminName(property.getAgencyAdmin().getFullName());
        }
        if (property.getBuyer() != null) {
            dto.setBuyerId(property.getBuyer().getId());
            dto.setBuyerName(property.getBuyer().getFullName());
            dto.setBuyerEmail(property.getBuyer().getEmail());
            dto.setBuyerTelephone(property.getBuyer().getTelephone());
        } else if ("VENDU".equals(property.getStatut()) || "LOUE".equals(property.getStatut())) {
            // Affiliate-originated sale: the buyer is a CRM lead (NEVER a User).
            // Lookup is bounded to terminal affiliate sales only → no N+1 on
            // available/normal properties.
            affiliateCustomerRelationRepository
                    .findFirstByPropertyIdOrderByCreatedAtDesc(property.getId())
                    .ifPresent(rel -> {
                        dto.setBuyerName(rel.getBuyerName());
                        dto.setBuyerEmail(rel.getBuyerEmail());
                        dto.setBuyerTelephone(rel.getBuyerPhone());
                        dto.setViaAffiliate(true);
                        if (rel.getAffiliate() != null) {
                            dto.setAffiliateName(rel.getAffiliate().getFullName());
                        }
                        dto.setAffiliateCommissionAmount(rel.getCommissionAmount());
                        dto.setAffiliateCommissionType(property.getCommissionType());
                        AffiliateTransaction atx = rel.getAffiliateTransaction();
                        if (atx != null) {
                            dto.setAffiliateCommissionPercentage(atx.getCommissionPercentage());
                            dto.setAffiliateCommissionPaid(atx.getIsPaid());
                        }
                    });
        }

        // Validation workflow + locks
        dto.setValidationStatus(property.getValidationStatus() != null ? property.getValidationStatus().name() : null);
        dto.setOwnerRole(property.getOwnerRole() != null ? property.getOwnerRole().name() : null);
        dto.setCommissionLocked(property.getCommissionLocked());
        dto.setPriceLocked(property.getPriceLocked());
        if (property.getCreatedBy() != null) {
            dto.setCreatedById(property.getCreatedBy().getId());
            dto.setCreatedByName(property.getCreatedBy().getFullName());
        }

        // Rental / finalized
        dto.setRentalStartDate(property.getRentalStartDate());
        dto.setRentalEndDate(property.getRentalEndDate());
        dto.setRentalDurationMonths(property.getRentalDurationMonths());
        dto.setIsFinalized(Boolean.TRUE.equals(property.getIsFinalized()));

        // Compute status lock (mirrors convertToFullDTO logic)
        String statut = property.getStatut();
        boolean lockedVendu = Boolean.TRUE.equals(property.getIsFinalized()) || "VENDU".equals(statut);
        boolean lockedEnAttente = "EN_ATTENTE".equals(statut) && Boolean.TRUE.equals(property.getIsReservedByAffiliate());
        boolean lockedLoue = "LOUE".equals(statut)
                && property.getRentalEndDate() != null
                && java.time.LocalDateTime.now().isBefore(property.getRentalEndDate());
        dto.setIsStatusLocked(lockedVendu || lockedEnAttente || lockedLoue);
        if (lockedVendu) {
            dto.setStatusLockReason("Ce bien est définitivement vendu. Aucune modification n'est possible.");
        } else if (lockedEnAttente) {
            dto.setStatusLockReason("Ce bien est en attente d'une offre affiliée en cours.");
        } else if (lockedLoue) {
            dto.setStatusLockReason("Bien loué jusqu'au " + property.getRentalEndDate().toLocalDate() + ". Statut verrouillé jusqu'à cette date.");
        }

        // Pending sale approval
        dto.setPendingSaleApproval(property.getPendingSaleApproval() != null ? property.getPendingSaleApproval().name() : null);
        dto.setPendingSaleStatut(property.getPendingSaleStatut());
        dto.setPendingSaleRejectionReason(property.getPendingSaleRejectionReason());
        dto.setPendingSaleApproverRole(property.getPendingSaleApproverRole() != null ? property.getPendingSaleApproverRole().name() : null);
        if (property.getPendingSaleRequestedBy() != null) {
            dto.setPendingSaleRequestedById(property.getPendingSaleRequestedBy().getId());
            dto.setPendingSaleRequestedByName(property.getPendingSaleRequestedBy().getFullName());
        }

        if (property.getMainImageId() != null) {
            dto.setHasMainImage(true);
            dto.setMainImageUrl("/api/images/public/" + property.getMainImageId());

            try {
                ImageDTO imageInfo = imageService.getImageInfoById(property.getMainImageId());
                dto.setMainImageName(imageInfo.getFileName());
                dto.setMainImageType(imageInfo.getFileType());
                dto.setMainImageSize(imageInfo.getFileSize());
            } catch (Exception e) {
                log.warn("Could not fetch image info for ID: {}", property.getMainImageId());
            }
        } else {
            dto.setHasMainImage(false);
        }

        if (property.getMainModel3dId() != null) {
            dto.setHasModel3d(true);
            dto.setModel3dUrl("/api/models/public/" + property.getMainModel3dId());

            try {
                Model3DDTO modelInfo = model3DService.getModel3DInfoById(property.getMainModel3dId());
                dto.setModel3dName(modelInfo.getFileName());
                dto.setModel3dType(modelInfo.getFileType());
                dto.setModel3dSize(modelInfo.getFileSize());
            } catch (Exception e) {
                log.warn("Could not fetch model info for ID: {}", property.getMainModel3dId());
            }
        } else {
            dto.setHasModel3d(false);
        }

        return dto;
    }

    private PropertyDTO convertToFullDTO(Property property) {
        long start = System.currentTimeMillis();
        Long propertyId = property != null ? property.getId() : null;
        log.info("➡️ Début convertToFullDTO pour propriété ID: {}", propertyId);
        PropertyDTO dto = new PropertyDTO();
        
        dto.setId(property.getId());
        dto.setTitre(property.getTitre());
        dto.setDescription(property.getDescription());
        dto.setType(property.getType());
        dto.setPrixVente(property.getPrixVente());
        dto.setPrixLocation(property.getPrixLocation());
        dto.setStatut(property.getStatut());
        dto.setSurface(property.getSurface());
        dto.setNbChambres(property.getNbChambres());
        dto.setNbSallesDeBain(property.getNbSallesDeBain());
        dto.setGarage(Boolean.TRUE.equals(property.getGarage()));
        dto.setPiscine(Boolean.TRUE.equals(property.getPiscine()));
        dto.setJardin(Boolean.TRUE.equals(property.getJardin()));
        dto.setMeuble(Boolean.TRUE.equals(property.getMeuble()));
        dto.setEtage(property.getEtage());
        dto.setParkingSpaces(property.getParkingSpaces());
        dto.setAnneeConstruction(property.getAnneeConstruction());
        dto.setProchePlage(Boolean.TRUE.equals(property.getProchePlage()));
        dto.setProcheTransport(Boolean.TRUE.equals(property.getProcheTransport()));
        dto.setSecurite(Boolean.TRUE.equals(property.getSecurite()));
        dto.setClimatisation(Boolean.TRUE.equals(property.getClimatisation()));
        dto.setAdresse(property.getAdresse());
        dto.setCountry(property.getCountry());
        dto.setCity(property.getCity());
        dto.setLatitude(property.getLatitude());
        dto.setLongitude(property.getLongitude());
        dto.setIsActive(property.getIsActive());
        dto.setCreatedAt(property.getCreatedAt());
        dto.setUpdatedAt(property.getUpdatedAt());
        dto.setCommissionPercentage(property.getCommissionPercentage());
        dto.setCommissionType(property.getCommissionType());

        // Ownership
        dto.setOwnerType(property.getOwnerType());
        dto.setIsAffiliateEligible(property.getIsAffiliateEligible());
        if (property.getAgencyAdmin() != null) {
            dto.setAgencyAdminId(property.getAgencyAdmin().getId());
            dto.setAgencyAdminName(property.getAgencyAdmin().getFullName());
        }
        dto.setSharedWithAgencyIds(sharedAgencyRepository.findAgencyAdminIdsByPropertyId(property.getId()));

        // Validation workflow + locks
        dto.setValidationStatus(property.getValidationStatus() != null ? property.getValidationStatus().name() : null);
        dto.setOwnerRole(property.getOwnerRole() != null ? property.getOwnerRole().name() : null);
        dto.setCommissionLocked(property.getCommissionLocked());
        dto.setPriceLocked(property.getPriceLocked());
        dto.setRejectionReason(property.getRejectionReason());
        if (property.getCreatedBy() != null) {
            dto.setCreatedById(property.getCreatedBy().getId());
            dto.setCreatedByName(property.getCreatedBy().getFullName());
        }

        // Rental lock fields
        dto.setRentalDurationMonths(property.getRentalDurationMonths());
        dto.setRentalStartDate(property.getRentalStartDate());
        dto.setRentalEndDate(property.getRentalEndDate());
        dto.setIsFinalized(Boolean.TRUE.equals(property.getIsFinalized()));
        // Compute status lock for the frontend
        String statut = property.getStatut();
        boolean lockedVendu = Boolean.TRUE.equals(property.getIsFinalized()) || "VENDU".equals(statut);
        boolean lockedEnAttente = "EN_ATTENTE".equals(statut) && Boolean.TRUE.equals(property.getIsReservedByAffiliate());
        boolean lockedLoue = "LOUE".equals(statut)
                && property.getRentalEndDate() != null
                && java.time.LocalDateTime.now().isBefore(property.getRentalEndDate());
        dto.setIsStatusLocked(lockedVendu || lockedEnAttente || lockedLoue);
        if (lockedVendu) {
            dto.setStatusLockReason("Ce bien est définitivement vendu. Aucune modification n'est possible.");
        } else if (lockedEnAttente) {
            dto.setStatusLockReason("Ce bien est en attente d'une offre affiliée en cours.");
        } else if (lockedLoue) {
            dto.setStatusLockReason("Bien loué jusqu'au " + property.getRentalEndDate().toLocalDate() + ". Statut verrouillé jusqu'à cette date.");
        }

        // Pending sale approval workflow
        dto.setPendingSaleApproval(property.getPendingSaleApproval() != null ? property.getPendingSaleApproval().name() : null);
        dto.setPendingSaleStatut(property.getPendingSaleStatut());
        dto.setPendingSaleRejectionReason(property.getPendingSaleRejectionReason());
        dto.setPendingSaleApproverRole(property.getPendingSaleApproverRole() != null ? property.getPendingSaleApproverRole().name() : null);
        if (property.getPendingSaleRequestedBy() != null) {
            dto.setPendingSaleRequestedById(property.getPendingSaleRequestedBy().getId());
            dto.setPendingSaleRequestedByName(property.getPendingSaleRequestedBy().getFullName());
        }

        if (property.getMainImageId() != null) {
            dto.setHasMainImage(true);
            dto.setMainImageUrl("/api/images/public/" + property.getMainImageId());

            try {
                ImageDTO imageInfo = imageService.getImageInfoById(property.getMainImageId());
                dto.setMainImageName(imageInfo.getFileName());
                dto.setMainImageType(imageInfo.getFileType());
                dto.setMainImageSize(imageInfo.getFileSize());
            } catch (Exception e) {
                log.warn("Could not fetch image info for ID: {}", property.getMainImageId());
            }
        } else {
            dto.setHasMainImage(false);
        }

        if (property.getMainModel3dId() != null) {
            dto.setHasModel3d(true);
            dto.setModel3dUrl("/api/models/public/" + property.getMainModel3dId());

            try {
                Model3DDTO modelInfo = model3DService.getModel3DInfoById(property.getMainModel3dId());
                dto.setModel3dName(modelInfo.getFileName());
                dto.setModel3dType(modelInfo.getFileType());
                dto.setModel3dSize(modelInfo.getFileSize());
            } catch (Exception e) {
                log.warn("Could not fetch model info for ID: {}", property.getMainModel3dId());
            }
        } else {
            dto.setHasModel3d(false);
        }

        List<PropertyMediaDTO> allMedia = new ArrayList<>();
        
        List<ImageDTO> images = imageService.getImagesInfoByPropertyId(property.getId());
        allMedia.addAll(images.stream().map(this::convertImageToMediaDTO).collect(Collectors.toList()));
        
        List<VideoDTO> videos = videoService.getVideosInfoByPropertyId(property.getId());
        allMedia.addAll(videos.stream().map(this::convertVideoToMediaDTO).collect(Collectors.toList()));
        
        Model3DDTO model = model3DService.getModel3DInfoByPropertyId(property.getId());
        if (model != null) {
            allMedia.add(convertModelToMediaDTO(model));
        }
        
        dto.setMedias(allMedia);
        long duration = System.currentTimeMillis() - start;
        log.info("⬅️ Fin convertToFullDTO pour propriété ID: {} en {} ms", propertyId, duration);
        return dto;
    }

    private PropertyMediaDTO convertImageToMediaDTO(ImageDTO image) {
        PropertyMediaDTO dto = new PropertyMediaDTO();
        dto.setId(image.getId());
        dto.setType("IMAGE");
        dto.setUrl("/api/images/public/" + image.getId());
        dto.setFileName(image.getFileName());
        dto.setFileType(image.getFileType());
        dto.setFileSize(image.getFileSize());
        dto.setSortOrder(image.getSortOrder());
        dto.setIsPrimary(image.getIsPrimary());
        dto.setPropertyId(image.getPropertyId());
        return dto;
    }

    private PropertyMediaDTO convertVideoToMediaDTO(VideoDTO video) {
        PropertyMediaDTO dto = new PropertyMediaDTO();
        dto.setId(video.getId());
        dto.setType("VIDEO");
        dto.setUrl("/api/videos/public/" + video.getId());
        dto.setFileName(video.getFileName());
        dto.setFileType(video.getFileType());
        dto.setFileSize(video.getFileSize());
        dto.setSortOrder(video.getSortOrder());
        dto.setIsPrimary(video.getIsPrimary());
        dto.setPropertyId(video.getPropertyId());
        return dto;
    }

    private PropertyMediaDTO convertModelToMediaDTO(Model3DDTO model) {
        PropertyMediaDTO dto = new PropertyMediaDTO();
        dto.setId(model.getId());
        dto.setType("MODEL_3D");
        dto.setUrl("/api/models/public/" + model.getId());
        dto.setFileName(model.getFileName());
        dto.setFileType(model.getFileType());
        dto.setFileSize(model.getFileSize());
        dto.setSortOrder(0);
        dto.setIsPrimary(true);
        dto.setPropertyId(model.getPropertyId());
        return dto;
    }

    public List<String> getAllRegions() {
    log.info("📋 Récupération de toutes les régions disponibles");
    List<String> regions = propertyRepository.findAllRegions();
    return regions;
}

    /**
     * Get allowed statuses for a specific category
     */
    public List<String> getAllowedStatusesForCategory(String category) {
        return Property.getAllowedStatusesForCategory(category);
    }
    
    /**
     * Get category for a property
     */
    public String getPropertyCategory(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec ID: " + id));
        return property.getCategory();
    }

}