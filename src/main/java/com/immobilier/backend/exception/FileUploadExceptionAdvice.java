package com.immobilier.backend.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.Map;

@ControllerAdvice
public class FileUploadExceptionAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        return ResponseEntity
            .badRequest()
            .body(Map.of(
                "error", "Fichier trop volumineux. Maximum: 1GB",  // ✅ CHANGÉ ICI
                "status", 400,
                "maxSize", exc.getMaxUploadSize()
            ));
    }
}