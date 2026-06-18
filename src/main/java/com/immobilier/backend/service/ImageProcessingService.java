package com.immobilier.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class ImageProcessingService {

    private static final int MAX_WIDTH = 1920;
    private static final int MAX_HEIGHT = 1080;
    private static final float JPEG_QUALITY = 0.85f;

    public byte[] processImage(byte[] imageData, String contentType) throws IOException {
        try {
            // Read image
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (originalImage == null) {
                throw new IOException("Could not read image data");
            }

            // Resize if necessary
            BufferedImage processedImage = resizeImageIfNeeded(originalImage);
            
            // Convert to appropriate format and compress
            return convertToBytes(processedImage, contentType);
            
        } catch (IOException e) {
            log.error("Error processing image: {}", e.getMessage(), e);
            throw new IOException("Failed to process image: " + e.getMessage(), e);
        }
    }

    private BufferedImage resizeImageIfNeeded(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        // Check if resizing is needed
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            return originalImage;
        }
        
        // Calculate new dimensions while maintaining aspect ratio
        double widthRatio = (double) MAX_WIDTH / width;
        double heightRatio = (double) MAX_HEIGHT / height;
        double ratio = Math.min(widthRatio, heightRatio);
        
        int newWidth = (int) (width * ratio);
        int newHeight = (int) (height * ratio);
        
        // Create resized image
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        graphics.dispose();
        
        log.info("Image resized from {}x{} to {}x{}", width, height, newWidth, newHeight);
        return resizedImage;
    }

    private byte[] convertToBytes(BufferedImage image, String contentType) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        if (contentType != null && contentType.contains("png")) {
            ImageIO.write(image, "png", outputStream);
        } else {
            // For JPEG and other formats, write as JPEG with quality setting
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            
            javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream);
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            writer.dispose();
            ios.close();
        }
        
        return outputStream.toByteArray();
    }
}