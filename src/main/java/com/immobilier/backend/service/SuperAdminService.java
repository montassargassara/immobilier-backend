package com.immobilier.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.immobilier.backend.dto.CreateUserRequest;
import com.immobilier.backend.dto.UserDTO;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.PropertyRepository;
import com.immobilier.backend.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {
    
    private final UserService userService;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // ========== STATISTIQUES PROPRIÉTÉS ==========
            long totalProperties = propertyRepository.count();
            long availableProperties = propertyRepository.countByStatut("DISPONIBLE");
            long soldProperties = propertyRepository.countByStatut("VENDU");
            long reservedProperties = propertyRepository.countByStatut("RESERVE");
            
            // ========== STATISTIQUES UTILISATEURS ==========
            long totalUsers = userRepository.count();
            long activeUsers = userRepository.countByIsActive(true);
            
            // COUNTERS BY ROLE
            long superAdminCount = userRepository.countByRole(RoleType.SUPER_ADMIN);
            long adminCount = userRepository.countByRole(RoleType.ADMIN);
            long responsableCommercialCount = userRepository.countByRole(RoleType.RESPONSABLE_COMMERCIAL);
            long commercialCount = userRepository.countByRole(RoleType.COMMERCIAL);
            long affiliateCount = userRepository.countByRole(RoleType.AFFILIATE);
            long clientCount = userRepository.countByRole(RoleType.CLIENT);
            
            // ========== STATISTIQUES REVENUS ==========
            Double monthlyRevenue = propertyRepository.calculateMonthlyRevenue();
            Double yearlyRevenue = propertyRepository.calculateYearlyRevenue();
            
            // ========== ZONES POPULAIRES ==========
            List<Object[]> popularAreas = propertyRepository.findPopularAreas();
            List<Map<String, Object>> areasList = new ArrayList<>();
            
            for (Object[] area : popularAreas) {
                Map<String, Object> areaMap = new HashMap<>();
                areaMap.put("area", area[0] != null ? area[0] : "Inconnu");
                areaMap.put("count", area[1] != null ? area[1] : 0);
                areasList.add(areaMap);
            }
            
            // ========== TYPES DE PROPRIÉTÉS ==========
            List<Object[]> propertyTypes = propertyRepository.findPropertyTypes();
            List<Map<String, Object>> typesList = new ArrayList<>();
            
            for (Object[] type : propertyTypes) {
                Map<String, Object> typeMap = new HashMap<>();
                typeMap.put("type", type[0] != null ? type[0] : "Inconnu");
                typeMap.put("count", type[1] != null ? type[1] : 0);
                typesList.add(typeMap);
            }
            
            // ========== AJOUT DE TOUTES LES STATS DANS LA MAP ==========
            // Stats propriétés
            stats.put("totalProperties", totalProperties);
            stats.put("availableProperties", availableProperties);
            stats.put("soldProperties", soldProperties);
            stats.put("reservedProperties", reservedProperties);
            
            // Stats utilisateurs - GÉNÉRALES
            stats.put("totalUsers", totalUsers);
            stats.put("activeUsers", activeUsers);
            stats.put("totalAgents", commercialCount + responsableCommercialCount);
            
            // Stats utilisateurs - PAR RÔLE
            stats.put("superAdminCount", superAdminCount);
            stats.put("adminCount", adminCount);
            stats.put("responsableCommercialCount", responsableCommercialCount);
            stats.put("commercialCount", commercialCount);
            stats.put("affiliateCount", affiliateCount);
            stats.put("clientCount", clientCount);
            
            // Stats revenus
            stats.put("monthlyRevenue", monthlyRevenue != null ? monthlyRevenue : 0);
            stats.put("yearlyRevenue", yearlyRevenue != null ? yearlyRevenue : 0);
            
            // Stats zones et types
            stats.put("popularAreas", areasList);
            stats.put("propertyTypes", typesList);
            
            log.info("Dashboard stats retrieved successfully");
            
        } catch (Exception e) {
            // ========== VALEURS PAR DÉFAUT EN CAS D'ERREUR ==========
            log.error("Error retrieving dashboard stats: {}", e.getMessage(), e);
            
            stats.put("totalProperties", 0);
            stats.put("availableProperties", 0);
            stats.put("soldProperties", 0);
            stats.put("reservedProperties", 0);
            
            stats.put("totalUsers", 0);
            stats.put("activeUsers", 0);
            stats.put("totalAgents", 0);
            
            // Valeurs par défaut pour les rôles
            stats.put("superAdminCount", 0);
            stats.put("adminCount", 0);
            stats.put("responsableCommercialCount", 0);
            stats.put("commercialCount", 0);
            stats.put("affiliateCount", 0);
            stats.put("clientCount", 0);
            
            stats.put("monthlyRevenue", 0);
            stats.put("yearlyRevenue", 0);
            stats.put("popularAreas", new ArrayList<>());
            stats.put("propertyTypes", new ArrayList<>());
        }
        
        return stats;
    }
    
    public UserDTO createAdmin(CreateUserRequest request) {
        request.setRole(RoleType.ADMIN);
        return userService.createUser(request);
    }
    
    public UserDTO createResponsable(CreateUserRequest request) {
        request.setRole(RoleType.RESPONSABLE_COMMERCIAL);
        return userService.createUser(request);
    }
    
    public void initFirstSuperAdmin() {
        authService.initSuperAdmin();
    }
}