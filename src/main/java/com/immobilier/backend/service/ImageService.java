package com.immobilier.backend.service;

import com.immobilier.backend.dto.ImageDTO;
import com.immobilier.backend.dto.ImageUploadRequest;
import com.immobilier.backend.entity.Image;
import com.immobilier.backend.entity.Property;
import com.immobilier.backend.repository.ImageRepository;
import com.immobilier.backend.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageRepository imageRepository;
    private final PropertyRepository propertyRepository;
    private final ImageProcessingService imageProcessingService;

    @Transactional(readOnly = true)
    public ImageDTO getImageById(Long imageId) {
        ImageDTO image = imageRepository.findActiveImageInfoById(imageId)
            .orElseThrow(() -> new RuntimeException("Image not found with ID: " + imageId));
        applyImageUrl(image);
        return image;
    }

        @Transactional(readOnly = true)
        public ImageDTO getImageInfoById(Long imageId) {
        ImageDTO image = imageRepository.findActiveImageInfoById(imageId)
            .orElseThrow(() -> new RuntimeException("Image not found with ID: " + imageId));
        applyImageUrl(image);
        return image;
        }

    @Transactional
    @CacheEvict(value = {"propertyImages", "propertyPrimaryImage"}, key = "#propertyId")
    public ImageDTO uploadImage(Long propertyId, MultipartFile file, ImageUploadRequest request) throws IOException {
        log.info("Uploading image for property ID: {}", propertyId);
        
        // Validate property exists
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found with ID: " + propertyId));
        
        // Validate file
        validateImageFile(file);
        
        // Process image (resize, compress)
        byte[] processedImage = imageProcessingService.processImage(file.getBytes(), file.getContentType());
        
        // Create image entity
        Image image = new Image();
        image.setPropertyId(propertyId);
        image.setFileName(file.getOriginalFilename());
        image.setFileType(file.getContentType());
        image.setFileSize((long) processedImage.length);
        image.setFileData(processedImage);
        
        if (request != null) {
            image.setAltText(request.getAltText());
            image.setTitle(request.getTitle());
        }
        
        // Get image dimensions
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(processedImage));
        if (bufferedImage != null) {
            image.setWidth(bufferedImage.getWidth());
            image.setHeight(bufferedImage.getHeight());
        }
        
        // Set sort order
        long imageCount = imageRepository.countActiveImagesByPropertyId(propertyId);
        image.setSortOrder((int) imageCount);
        
        // Handle primary flag
        boolean isPrimary = request != null && request.isPrimary();
        if (isPrimary) {
            imageRepository.resetPrimaryFlag(propertyId);
            image.setIsPrimary(true);
        }
        
        Image savedImage = imageRepository.save(image);
        
        // Update property's main image reference if this is primary
        if (isPrimary) {
            property.setMainImageId(savedImage.getId());
            propertyRepository.save(property);
        }
        
        log.info("Image uploaded successfully with ID: {}", savedImage.getId());
        
        return convertToDTO(savedImage);
    }

    @Transactional
    @CacheEvict(value = {"propertyImages", "propertyPrimaryImage"}, key = "#propertyId")
    public ImageDTO setPrimaryImage(Long imageId, Long propertyId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found with ID: " + imageId));

        if (!image.getPropertyId().equals(propertyId)) {
            throw new RuntimeException("Image does not belong to property " + propertyId);
        }

        imageRepository.resetPrimaryFlag(propertyId);
        image.setIsPrimary(true);
        Image saved = imageRepository.save(image);

        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property != null) {
            property.setMainImageId(saved.getId());
            propertyRepository.save(property);
        }

        log.info("Primary image updated to ID: {} for property ID: {}", imageId, propertyId);
        return convertToDTO(saved);
    }

    @Transactional
    public void deleteAllImagesByPropertyId(Long propertyId) {
        log.info("Deleting all images for property ID: {}", propertyId);
        
        // Use the correct method that returns all images by propertyId
        List<Image> images = imageRepository.findByPropertyId(propertyId);
        
        // Soft delete each image
        for (Image image : images) {
            image.setIsActive(false);
            imageRepository.save(image);
        }
        
        log.info("Deleted {} images for property ID: {}", images.size(), propertyId);
    }

    public List<ImageDTO> getImagesByPropertyId(Long propertyId) {
        return getImagesInfoByPropertyId(propertyId);
    }

    public List<ImageDTO> getImagesInfoByPropertyId(Long propertyId) {
        List<ImageDTO> images = imageRepository.findActiveImagesInfoByPropertyId(propertyId);
        images.forEach(this::applyImageUrl);
        return images;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "propertyPrimaryImage", key = "#propertyId")
    public ImageDTO getPrimaryImage(Long propertyId) {
        return imageRepository.findActivePrimaryImageInfoByPropertyId(propertyId)
                .map(image -> {
                    applyImageUrl(image);
                    return image;
                })
                .orElse(null);
        }

        @Transactional(readOnly = true)
        public ImageDTO getPrimaryImageInfo(Long propertyId) {
        return imageRepository.findActivePrimaryImageInfoByPropertyId(propertyId)
                .map(image -> {
                    applyImageUrl(image);
                    return image;
                })
                .orElse(null);
    }

    @Transactional
    @CacheEvict(value = {"propertyImages", "propertyPrimaryImage"}, key = "#propertyId")
    public void deleteImage(Long imageId, Long propertyId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found with ID: " + imageId));
        
        Integer sortOrder = image.getSortOrder();
        
        // Soft delete the image
        image.setIsActive(false);
        imageRepository.save(image);
        
        // Reorder remaining images
        imageRepository.decrementSortOrder(propertyId, sortOrder);
        
        // Update property's main image reference if needed
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property != null && property.getMainImageId() != null && 
            property.getMainImageId().equals(imageId)) {
            property.setMainImageId(null);
            propertyRepository.save(property);
        }
        
        log.info("Image deleted successfully with ID: {}", imageId);
    }

    @Transactional
    @CacheEvict(value = {"propertyImages", "propertyPrimaryImage"}, key = "#propertyId")
    public void reorderImages(Long propertyId, List<Long> imageIds) {
        int order = 0;
        for (Long imageId : imageIds) {
            Image image = imageRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("Image not found: " + imageId));
            image.setSortOrder(order++);
            imageRepository.save(image);
        }
        log.info("Images reordered for property ID: {}", propertyId);
    }

    public byte[] getImageData(Long imageId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found with ID: " + imageId));
        return image.getFileData();
    }

    private void validateImageFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Invalid file type. Only images are allowed.");
        }
        
        if (file.getSize() > 50 * 1024 * 1024) { // 50MB max
            throw new RuntimeException("File too large. Maximum size: 50MB");
        }
    }

    private ImageDTO convertToDTO(Image image) {
        ImageDTO dto = new ImageDTO();
        dto.setId(image.getId());
        dto.setPropertyId(image.getPropertyId());
        dto.setFileName(image.getFileName());
        dto.setFileType(image.getFileType());
        dto.setFileSize(image.getFileSize());
        dto.setWidth(image.getWidth());
        dto.setHeight(image.getHeight());
        dto.setAltText(image.getAltText());
        dto.setTitle(image.getTitle());
        dto.setIsPrimary(image.getIsPrimary());
        dto.setSortOrder(image.getSortOrder());
        dto.setUrl("/api/images/public/" + image.getId());
        dto.setCreatedAt(image.getCreatedAt());
        return dto;
    }

    private void applyImageUrl(ImageDTO image) {
        if (image != null) {
            image.setUrl("/api/images/public/" + image.getId());
        }
    }
}