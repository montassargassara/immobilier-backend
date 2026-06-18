package com.immobilier.backend.service;

import com.immobilier.backend.dto.ImageDTO;
import com.immobilier.backend.dto.Model3DDTO;
import com.immobilier.backend.dto.PublicAgencyDTO;
import com.immobilier.backend.dto.PublicPropertyCardDTO;
import com.immobilier.backend.dto.PublicPropertyDetailDTO;
import com.immobilier.backend.dto.VideoDTO;
import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Public-facing portal service. Exposes only the data buyers/visitors need —
 * prices, location, images. No commission or affiliate metadata leaks here.
 *
 * Visibility rule: any property where {@code isActive = true} and
 * {@code statut = 'DISPONIBLE'} is browsable by visitors. Multi-tenant access
 * control (agency isolation, share-request approval) only matters for the
 * internal admin views — the public site is the marketplace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicPortalService {

    private static final String IMAGE_URL_PREFIX = "/api/images/public/";
    private static final String VIDEO_URL_PREFIX = "/api/videos/public/";
    private static final String MODEL_URL_PREFIX = "/api/models/public/";
    private static final String STATUT_DISPONIBLE = "DISPONIBLE";

    // ── Platform official contact (SUPER_ADMIN-owned listings) ──────────────
    @Value("${platform.contact.phone:+216 71 000 000}")
    private String platformPhone;

    @Value("${platform.contact.email:contact@maison3d.tn}")
    private String platformEmail;

    @Value("${platform.contact.address:Tunis, Tunisie}")
    private String platformAddress;

    @Value("${platform.contact.whatsapp:21671000000}")
    private String platformWhatsapp;

    private final PropertyRepository propertyRepository;
    private final ImageService imageService;
    private final VideoService videoService;
    private final Model3DService model3DService;

    public List<PublicPropertyCardDTO> listForSale(PublicSearchFilters filters) {
        return browsable()
                .filter(this::isForSale)
                .filter(p -> matches(p, filters))
                .sorted(Comparator.comparing(Property::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toCardDTO)
                .collect(Collectors.toList());
    }

    public List<PublicPropertyCardDTO> listForRent(PublicSearchFilters filters) {
        return browsable()
                .filter(this::isForRent)
                .filter(p -> matches(p, filters))
                .sorted(Comparator.comparing(Property::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toCardDTO)
                .collect(Collectors.toList());
    }

    public List<PublicPropertyCardDTO> featuredForSale(int limit) {
        return browsable()
                .filter(this::isForSale)
                .sorted(Comparator.comparing(Property::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(this::toCardDTO)
                .collect(Collectors.toList());
    }

    public List<PublicPropertyCardDTO> featuredForRent(int limit) {
        return browsable()
                .filter(this::isForRent)
                .sorted(Comparator.comparing(Property::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(this::toCardDTO)
                .collect(Collectors.toList());
    }

    public PublicPropertyDetailDTO getDetail(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée"));
        if (Boolean.FALSE.equals(property.getIsActive())) {
            throw new RuntimeException("Propriété non disponible");
        }
        return toDetailDTO(property);
    }

    public List<PublicPropertyCardDTO> similarTo(Long id, int limit) {
        Property base = propertyRepository.findById(id).orElse(null);
        if (base == null) return List.of();

        String category = base.getCategory();
        return browsable()
                .filter(p -> !p.getId().equals(base.getId()))
                .filter(p -> category == null || category.equals(p.getCategory()))
                .sorted(Comparator.comparingInt((Property p) -> similarityScore(base, p)).reversed())
                .limit(limit)
                .map(this::toCardDTO)
                .collect(Collectors.toList());
    }

    public List<String> distinctCountries() {
        return browsable()
                .map(Property::getCountry)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public List<String> distinctCities(String country) {
        return browsable()
                .filter(p -> country == null || country.isBlank()
                        || country.equalsIgnoreCase(p.getCountry()))
                .map(Property::getCity)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public List<String> distinctTypes() {
        return browsable()
                .map(Property::getType)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Internals
    // ────────────────────────────────────────────────────────────────────────

    private java.util.stream.Stream<Property> browsable() {
        return propertyRepository.findByIsActiveTrue().stream()
                .filter(p -> STATUT_DISPONIBLE.equalsIgnoreCase(p.getStatut()))
                // Only APPROVED properties are exposed to the public portal.
                // Legacy rows (validationStatus = null) are treated as approved.
                .filter(p -> p.getValidationStatus() == null
                        || p.getValidationStatus() == com.immobilier.backend.enums.PropertyValidationStatus.APPROVED);
    }

    private boolean isForSale(Property p) {
        return p.getPrixVente() != null && p.getPrixVente() > 0;
    }

    private boolean isForRent(Property p) {
        return p.getPrixLocation() != null && p.getPrixLocation() > 0;
    }

    private boolean matches(Property p, PublicSearchFilters f) {
        if (f == null) return true;
        if (f.country != null && !f.country.isBlank()
                && !f.country.equalsIgnoreCase(p.getCountry())) return false;
        if (f.city != null && !f.city.isBlank()
                && !f.city.equalsIgnoreCase(p.getCity())) return false;
        if (f.type != null && !f.type.isBlank()
                && !f.type.equalsIgnoreCase(p.getType())) return false;
        if (f.minSurface != null && (p.getSurface() == null || p.getSurface() < f.minSurface)) return false;
        if (f.maxSurface != null && (p.getSurface() == null || p.getSurface() > f.maxSurface)) return false;
        if (f.minRooms != null && (p.getNbChambres() == null || p.getNbChambres() < f.minRooms)) return false;

        Double price = isForSale(p) ? p.getPrixVente() : p.getPrixLocation();
        if (f.minPrice != null && (price == null || price < f.minPrice)) return false;
        if (f.maxPrice != null && (price == null || price > f.maxPrice)) return false;

        if (f.q != null && !f.q.isBlank()) {
            String needle = f.q.toLowerCase(Locale.ROOT).trim();
            String haystack = String.join(" ",
                    nullSafe(p.getTitre()),
                    nullSafe(p.getDescription()),
                    nullSafe(p.getCity()),
                    nullSafe(p.getCountry()),
                    nullSafe(p.getRegion()),
                    nullSafe(p.getAdresse())).toLowerCase(Locale.ROOT);
            if (!haystack.contains(needle)) return false;
        }
        return true;
    }

    private int similarityScore(Property base, Property other) {
        int score = 0;
        if (eqIgnoreCase(base.getCity(), other.getCity())) score += 5;
        if (eqIgnoreCase(base.getCountry(), other.getCountry())) score += 2;
        if (eqIgnoreCase(base.getType(), other.getType())) score += 3;
        if (base.getNbChambres() != null && base.getNbChambres().equals(other.getNbChambres())) score += 1;
        return score;
    }

    private boolean eqIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private PublicPropertyCardDTO toCardDTO(Property p) {
        PublicPropertyCardDTO dto = new PublicPropertyCardDTO();
        dto.setId(p.getId());
        dto.setTitre(p.getTitre());
        dto.setType(p.getType());
        dto.setCategory(p.getCategory());
        dto.setPrixVente(p.getPrixVente());
        dto.setPrixLocation(p.getPrixLocation());
        dto.setSurface(p.getSurface());
        dto.setNbChambres(p.getNbChambres());
        dto.setNbSallesDeBain(p.getNbSallesDeBain());
        dto.setGarage(Boolean.TRUE.equals(p.getGarage()));
        dto.setPiscine(Boolean.TRUE.equals(p.getPiscine()));
        dto.setJardin(Boolean.TRUE.equals(p.getJardin()));
        dto.setMeuble(Boolean.TRUE.equals(p.getMeuble()));
        dto.setEtage(p.getEtage());
        dto.setParkingSpaces(p.getParkingSpaces());
        dto.setClimatisation(Boolean.TRUE.equals(p.getClimatisation()));
        dto.setSecurite(Boolean.TRUE.equals(p.getSecurite()));
        dto.setCity(p.getCity());
        dto.setCountry(p.getCountry());
        dto.setRegion(p.getRegion());
        dto.setLatitude(p.getLatitude());
        dto.setLongitude(p.getLongitude());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setHasModel3d(p.getMainModel3dId() != null);
        if (p.getMainImageId() != null) {
            dto.setMainImageUrl(IMAGE_URL_PREFIX + p.getMainImageId());
        }
        PublicAgencyDTO agency = buildAgencyDTO(p);
        dto.setAgency(agency);
        dto.setAgencyName(agency.getName());
        return dto;
    }

    private PublicPropertyDetailDTO toDetailDTO(Property p) {
        PublicPropertyDetailDTO dto = new PublicPropertyDetailDTO();
        dto.setId(p.getId());
        dto.setTitre(p.getTitre());
        dto.setDescription(p.getDescription());
        dto.setType(p.getType());
        dto.setCategory(p.getCategory());
        dto.setStatut(p.getStatut());
        dto.setPrixVente(p.getPrixVente());
        dto.setPrixLocation(p.getPrixLocation());
        dto.setSurface(p.getSurface());
        dto.setNbChambres(p.getNbChambres());
        dto.setNbSallesDeBain(p.getNbSallesDeBain());
        dto.setGarage(Boolean.TRUE.equals(p.getGarage()));
        dto.setPiscine(Boolean.TRUE.equals(p.getPiscine()));
        dto.setJardin(Boolean.TRUE.equals(p.getJardin()));
        dto.setMeuble(Boolean.TRUE.equals(p.getMeuble()));
        dto.setEtage(p.getEtage());
        dto.setParkingSpaces(p.getParkingSpaces());
        dto.setAnneeConstruction(p.getAnneeConstruction());
        dto.setProchePlage(Boolean.TRUE.equals(p.getProchePlage()));
        dto.setProcheTransport(Boolean.TRUE.equals(p.getProcheTransport()));
        dto.setSecurite(Boolean.TRUE.equals(p.getSecurite()));
        dto.setClimatisation(Boolean.TRUE.equals(p.getClimatisation()));
        dto.setAdresse(p.getAdresse());
        dto.setCity(p.getCity());
        dto.setCountry(p.getCountry());
        dto.setRegion(p.getRegion());
        dto.setLatitude(p.getLatitude());
        dto.setLongitude(p.getLongitude());
        dto.setCreatedAt(p.getCreatedAt());

        if (p.getMainImageId() != null) {
            dto.setMainImageUrl(IMAGE_URL_PREFIX + p.getMainImageId());
        }

        try {
            List<ImageDTO> images = imageService.getImagesInfoByPropertyId(p.getId());
            List<String> urls = images.stream()
                    .map(img -> IMAGE_URL_PREFIX + img.getId())
                    .collect(Collectors.toList());
            if (dto.getMainImageUrl() != null && !urls.contains(dto.getMainImageUrl())) {
                urls.add(0, dto.getMainImageUrl());
            }
            dto.setImageUrls(urls);
        } catch (Exception e) {
            log.warn("Could not fetch images for property {}: {}", p.getId(), e.getMessage());
            dto.setImageUrls(List.of());
        }

        if (p.getMainModel3dId() != null) {
            dto.setHasModel3d(true);
            dto.setModel3dUrl(MODEL_URL_PREFIX + p.getMainModel3dId());
            try {
                Model3DDTO modelInfo = model3DService.getModel3DInfoById(p.getMainModel3dId());
                if (modelInfo != null && modelInfo.getFormat() != null) {
                    dto.setModel3dFormat(modelInfo.getFormat());
                }
            } catch (Exception ignored) {}
        }

        try {
            List<VideoDTO> videos = videoService.getVideosInfoByPropertyId(p.getId());
            List<String> videoUrls = videos.stream()
                    .map(v -> VIDEO_URL_PREFIX + v.getId())
                    .collect(Collectors.toList());
            dto.setVideoUrls(videoUrls);
            if (p.getMainVideoId() != null) {
                dto.setHasVideo(true);
                dto.setMainVideoUrl(VIDEO_URL_PREFIX + p.getMainVideoId());
            } else if (!videoUrls.isEmpty()) {
                dto.setHasVideo(true);
                dto.setMainVideoUrl(videoUrls.get(0));
            }
        } catch (Exception e) {
            log.warn("Could not fetch videos for property {}: {}", p.getId(), e.getMessage());
            dto.setVideoUrls(List.of());
        }

        PublicAgencyDTO agency = buildAgencyDTO(p);
        dto.setAgency(agency);
        dto.setAgencyName(agency.getName());
        if (p.getAgencyAdmin() != null) {
            dto.setAgencyAdminId(p.getAgencyAdmin().getId());
        }

        return dto;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Agency DTO builder
    // ────────────────────────────────────────────────────────────────────────

    private PublicAgencyDTO buildAgencyDTO(Property p) {
        PublicAgencyDTO dto = new PublicAgencyDTO();

        boolean isSuperAdminOwned = "SUPER_ADMIN_OWNED".equals(p.getOwnerType())
                || p.getAgencyAdmin() == null;

        if (isSuperAdminOwned) {
            dto.setId(null);
            dto.setName("Maison3D Immobilier");
            dto.setType("SUPER_ADMIN");
            dto.setPhone(platformPhone);
            dto.setEmail(platformEmail);
            dto.setAddress(platformAddress);
            dto.setWhatsappLink("https://wa.me/" + platformWhatsapp.replaceAll("[^0-9]", ""));
            dto.setLogoUrl(null);
        } else {
            User admin = p.getAgencyAdmin();
            dto.setId(admin.getId());
            dto.setName(buildAgencyDisplayName(admin));
            dto.setType("AGENCY");
            dto.setPhone(admin.getTelephone());
            dto.setEmail(admin.getEmail());
            dto.setAddress(null);
            dto.setWhatsappLink(buildWhatsappLink(admin.getTelephone()));
            dto.setLogoUrl(buildLogoUrl(admin));
        }

        return dto;
    }

    private String buildAgencyDisplayName(User admin) {
        String full = admin.getFullName();
        return (full != null && !full.isBlank()) ? full : admin.getEmail();
    }

    private String buildWhatsappLink(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.isBlank()) return null;
        return "https://wa.me/" + digits.replace("+", "");
    }

    private String buildLogoUrl(User admin) {
        // Logo serving via a public endpoint is a future feature.
        // The frontend falls back to styled initials when null.
        return null;
    }

    @lombok.Data
    public static class PublicSearchFilters {
        private String q;
        private String country;
        private String city;
        private String type;
        private Double minPrice;
        private Double maxPrice;
        private Double minSurface;
        private Double maxSurface;
        private Integer minRooms;
    }
}
