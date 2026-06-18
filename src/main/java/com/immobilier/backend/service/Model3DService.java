package com.immobilier.backend.service;

import com.immobilier.backend.dto.Model3DDTO;
import com.immobilier.backend.entity.Model3D;
import com.immobilier.backend.entity.Property;
import com.immobilier.backend.repository.Model3DRepository;
import com.immobilier.backend.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class Model3DService {

    private final Model3DRepository model3DRepository;
    private final PropertyRepository propertyRepository;

    @Transactional(readOnly = true)
    public Model3DDTO getModel3DById(Long modelId) {
        Model3DDTO model = model3DRepository.findActiveModelInfoById(modelId)
            .orElseThrow(() -> new RuntimeException("3D model not found with ID: " + modelId));
        applyModelUrl(model);
        return model;
    }

        @Transactional(readOnly = true)
        public Model3DDTO getModel3DInfoById(Long modelId) {
        Model3DDTO model = model3DRepository.findActiveModelInfoById(modelId)
            .orElseThrow(() -> new RuntimeException("3D model not found with ID: " + modelId));
        applyModelUrl(model);
        return model;
        }

    @Transactional
    public Model3DDTO uploadModel3D(Long propertyId, MultipartFile file, String description) throws IOException {
        log.info("Uploading 3D model for property ID: {}", propertyId);
        
        // Check if property exists
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found with ID: " + propertyId));
        
        validateModelFile(file);
        
        // Soft delete existing models
        List<Model3D> existingModels = model3DRepository.findByPropertyId(propertyId);
        for (Model3D existingModel : existingModels) {
            existingModel.setIsActive(false);
            model3DRepository.save(existingModel);
        }
        
        // Create new model
        Model3D model = new Model3D();
        model.setPropertyId(propertyId);
        model.setFileName(file.getOriginalFilename());
        model.setFileType(file.getContentType());
        model.setFileSize(file.getSize());
        model.setFileData(file.getBytes());
        model.setDescription(description);
        model.setIsActive(true);
        
        // Determine format from file name and content type
        String fileName = file.getOriginalFilename().toLowerCase();
        if (fileName.endsWith(".ksplat")) {
            model.setFormat("ksplat");
        } else if (fileName.endsWith(".splat")) {
            model.setFormat("splat");
        } else if (fileName.endsWith(".gltf")) {
            model.setFormat("gltf");
        } else if (fileName.endsWith(".glb")) {
            model.setFormat("glb");
        } else if (fileName.endsWith(".obj")) {
            model.setFormat("obj");
        } else if (fileName.endsWith(".fbx")) {
            model.setFormat("fbx");
        } else if (fileName.endsWith(".ply")) {
            model.setFormat("ply");
        } else if (file.getContentType() != null) {
            if (file.getContentType().contains("gltf")) {
                model.setFormat("gltf");
            } else if (file.getContentType().contains("obj")) {
                model.setFormat("obj");
            } else if (file.getContentType().contains("fbx")) {
                model.setFormat("fbx");
            } else if (file.getContentType().contains("ply")) {
                model.setFormat("ply");
            } else {
                model.setFormat("glb");
            }
        }
        
        Model3D savedModel = model3DRepository.save(model);
        
        // Update property with main model ID
        property.setMainModel3dId(savedModel.getId());
        propertyRepository.save(property);
        
        log.info("3D model uploaded successfully with ID: {}", savedModel.getId());
        
        return convertToDTO(savedModel);
    }

    @Transactional
    public void deleteModel3DByPropertyId(Long propertyId) {
        log.info("Deleting 3D models for property ID: {}", propertyId);
        
        List<Model3D> models = model3DRepository.findByPropertyId(propertyId);
        
        for (Model3D model : models) {
            model.setIsActive(false);
            model3DRepository.save(model);
        }
        
        // Clear the reference in property
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property != null) {
            property.setMainModel3dId(null);
            propertyRepository.save(property);
        }
        
        log.info("Deleted {} 3D models for property ID: {}", models.size(), propertyId);
    }

    @Transactional(readOnly = true)
    public Model3DDTO getModel3DByPropertyId(Long propertyId) {
        return getModel3DInfoByPropertyId(propertyId);
    }

    @Transactional(readOnly = true)
    public Model3DDTO getModel3DInfoByPropertyId(Long propertyId) {
        return model3DRepository.findActiveModelsInfoByPropertyId(propertyId)
                .stream()
                .findFirst()
                .map(model -> {
                    applyModelUrl(model);
                    return model;
                })
                .orElse(null);
    }

    @Transactional
    public void deleteModel3D(Long modelId, Long propertyId) {
        Model3D model = model3DRepository.findById(modelId)
                .orElseThrow(() -> new RuntimeException("3D model not found with ID: " + modelId));
        
        model.setIsActive(false);
        model3DRepository.save(model);
        
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property != null && property.getMainModel3dId() != null && 
            property.getMainModel3dId().equals(modelId)) {
            property.setMainModel3dId(null);
            propertyRepository.save(property);
        }
        
        log.info("3D model deleted successfully with ID: {}", modelId);
    }

    @Transactional(readOnly = true)
    public byte[] getModel3DData(Long modelId) {
        Model3D model = model3DRepository.findById(modelId)
                .orElseThrow(() -> new RuntimeException("3D model not found with ID: " + modelId));

        if (!model.getIsActive()) {
            throw new RuntimeException("Model is not active");
        }

        // File-based storage (used for large gaussian splat files accepted via the pipeline)
        if (model.getModelPath() != null && !model.getModelPath().isBlank()) {
            try {
                return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(model.getModelPath()));
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to read model file: " + model.getModelPath(), e);
            }
        }

        return model.getFileData();
    }

    private void validateModelFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        
        // Check by file extension
        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            if (lowerFileName.endsWith(".glb") || lowerFileName.endsWith(".gltf")
                    || lowerFileName.endsWith(".obj") || lowerFileName.endsWith(".fbx")
                    || lowerFileName.endsWith(".ply")
                    || lowerFileName.endsWith(".ksplat") || lowerFileName.endsWith(".splat")) {
                return; // Valid by extension
            }
        }

        // Check by content type (browsers send application/octet-stream for binary 3D files)
        if (contentType != null) {
            if (contentType.contains("gltf") || contentType.contains("glb")
                    || contentType.contains("obj") || contentType.contains("fbx")
                    || contentType.contains("ply") || contentType.contains("model")
                    || contentType.equals("application/octet-stream")) {
                return; // Valid by content type
            }
        }

        throw new RuntimeException("Format non supporté. Formats acceptés : GLB, GLTF, OBJ, FBX, PLY");
    }

    private Model3DDTO convertToDTO(Model3D model) {
        Model3DDTO dto = new Model3DDTO();
        dto.setId(model.getId());
        dto.setPropertyId(model.getPropertyId());
        dto.setFileName(model.getFileName());
        dto.setFileType(model.getFileType());
        dto.setFileSize(model.getFileSize());
        dto.setFormat(model.getFormat());
        dto.setPolygonCount(model.getPolygonCount());
        dto.setDescription(model.getDescription());
        dto.setUrl("/api/models/public/" + model.getId());
        dto.setCreatedAt(model.getCreatedAt());
        return dto;
    }

    private void applyModelUrl(Model3DDTO model) {
        if (model != null) {
            model.setUrl("/api/models/public/" + model.getId());
        }
    }
}