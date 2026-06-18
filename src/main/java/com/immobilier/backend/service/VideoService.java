package com.immobilier.backend.service;

import com.immobilier.backend.dto.VideoDTO;
import com.immobilier.backend.dto.VideoMetadata;
import com.immobilier.backend.dto.VideoUploadRequest;
import com.immobilier.backend.entity.Video;
import com.immobilier.backend.entity.Property;
import com.immobilier.backend.repository.VideoRepository;
import com.immobilier.backend.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final PropertyRepository propertyRepository;
    private final VideoProcessingService videoProcessingService;

    @Transactional(readOnly = true)
    public VideoDTO getVideoById(Long videoId) {
        VideoDTO video = videoRepository.findActiveVideoInfoById(videoId)
            .orElseThrow(() -> new RuntimeException("Video not found with ID: " + videoId));
        applyVideoUrls(video);
        return video;
        }

        @Transactional(readOnly = true)
        public VideoDTO getVideoInfoById(Long videoId) {
        VideoDTO video = videoRepository.findActiveVideoInfoById(videoId)
            .orElseThrow(() -> new RuntimeException("Video not found with ID: " + videoId));
        applyVideoUrls(video);
        return video;
    }

    @Transactional
    @CacheEvict(value = {"propertyVideos", "propertyPrimaryVideo"}, key = "#propertyId")
    public VideoDTO uploadVideo(Long propertyId, MultipartFile file, VideoUploadRequest request) throws IOException {
        log.info("Uploading video for property ID: {}", propertyId);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found with ID: " + propertyId));
        
        validateVideoFile(file);
        
        // Process video (extract metadata, generate thumbnail)
        VideoMetadata metadata = videoProcessingService.processVideo(file.getBytes(), file.getContentType());
        
        Video video = new Video();
        video.setPropertyId(propertyId);
        video.setFileName(file.getOriginalFilename());
        video.setFileType(file.getContentType());
        video.setFileSize(file.getSize());
        video.setFileData(file.getBytes());
        video.setDuration(metadata.getDuration());
        video.setWidth(metadata.getWidth());
        video.setHeight(metadata.getHeight());
        video.setThumbnail(metadata.getThumbnail());
        
        if (request != null) {
            video.setTitle(request.getTitle());
            video.setDescription(request.getDescription());
        }
        
        // Set sort order - using the correct method name
        long videoCount = videoRepository.countActiveVideosByPropertyId(propertyId);
        video.setSortOrder((int) videoCount);
        
        // Handle primary flag
        boolean isPrimary = request != null && request.isPrimary();
        if (isPrimary) {
            videoRepository.resetPrimaryFlag(propertyId);
            video.setIsPrimary(true);
        }
        
        Video savedVideo = videoRepository.save(video);
        
        // Update property's main video reference if this is primary
        if (isPrimary) {
            property.setMainVideoId(savedVideo.getId());
            propertyRepository.save(property);
        }
        
        log.info("Video uploaded successfully with ID: {}", savedVideo.getId());
        
        return convertToDTO(savedVideo);
    }

    @Transactional
    public void deleteAllVideosByPropertyId(Long propertyId) {
        log.info("Deleting all videos for property ID: {}", propertyId);
        
        // Use the method that returns all videos by propertyId
        List<Video> videos = videoRepository.findByPropertyId(propertyId);
        
        // Soft delete each video
        for (Video video : videos) {
            video.setIsActive(false);
            videoRepository.save(video);
        }
        
        log.info("Deleted {} videos for property ID: {}", videos.size(), propertyId);
    }

    public List<VideoDTO> getVideosByPropertyId(Long propertyId) {
        return getVideosInfoByPropertyId(propertyId);
    }

    public List<VideoDTO> getVideosInfoByPropertyId(Long propertyId) {
        List<VideoDTO> videos = videoRepository.findActiveVideosInfoByPropertyId(propertyId);
        videos.forEach(this::applyVideoUrls);
        return videos;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "propertyPrimaryVideo", key = "#propertyId")
    public VideoDTO getPrimaryVideo(Long propertyId) {
        return videoRepository.findActivePrimaryVideoInfoByPropertyId(propertyId)
                .map(video -> {
                    applyVideoUrls(video);
                    return video;
                })
                .orElse(null);
    }

    @Transactional
    @CacheEvict(value = {"propertyVideos", "propertyPrimaryVideo"}, key = "#propertyId")
    public void deleteVideo(Long videoId, Long propertyId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found with ID: " + videoId));
        
        // Soft delete the video
        video.setIsActive(false);
        videoRepository.save(video);
        
        // Update property's main video reference if needed
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property != null && property.getMainVideoId() != null && 
            property.getMainVideoId().equals(videoId)) {
            property.setMainVideoId(null);
            propertyRepository.save(property);
        }
        
        log.info("Video deleted successfully with ID: {}", videoId);
    }

    public byte[] getVideoData(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found with ID: " + videoId));
        return video.getFileData();
    }
    
    public byte[] getVideoThumbnail(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found with ID: " + videoId));
        return video.getThumbnail();
    }

    private void validateVideoFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new RuntimeException("Invalid file type. Only videos are allowed.");
        }
        
        if (file.getSize() > 500 * 1024 * 1024) { // 500MB max
            throw new RuntimeException("File too large. Maximum size: 500MB");
        }
    }

    private VideoDTO convertToDTO(Video video) {
        VideoDTO dto = new VideoDTO();
        dto.setId(video.getId());
        dto.setPropertyId(video.getPropertyId());
        dto.setFileName(video.getFileName());
        dto.setFileType(video.getFileType());
        dto.setFileSize(video.getFileSize());
        dto.setDuration(video.getDuration());
        dto.setWidth(video.getWidth());
        dto.setHeight(video.getHeight());
        dto.setTitle(video.getTitle());
        dto.setDescription(video.getDescription());
        dto.setIsPrimary(video.getIsPrimary());
        dto.setSortOrder(video.getSortOrder());
        dto.setUrl("/api/videos/public/" + video.getId());
        dto.setThumbnailUrl("/api/videos/public/" + video.getId() + "/thumbnail");
        dto.setCreatedAt(video.getCreatedAt());
        return dto;
    }

    private void applyVideoUrls(VideoDTO video) {
        if (video != null) {
            video.setUrl("/api/videos/public/" + video.getId());
            video.setThumbnailUrl("/api/videos/public/" + video.getId() + "/thumbnail");
        }
    }
}