
package com.replit.controller;

import com.replit.service.AccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api")
public class AudioController {

    private static final Logger logger = LoggerFactory.getLogger(AudioController.class);

    private final AccessService accessService;

    public AudioController(AccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/audio/stream/{resourceId}")
    public ResponseEntity<Resource> streamAudio(@PathVariable String resourceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();

        logger.info("Audio stream request for resource {} by user {}", resourceId, userId);

        // Sprawdź uprawnienia użytkownika
        if (!accessService.checkAccess(userId, resourceId)) {
            logger.warn("Access denied for user {} to resource {}", userId, resourceId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // Przykładowa ścieżka do plików audio
            Path audioPath = Paths.get("audio-files", resourceId + ".mp3");
            File audioFile = audioPath.toFile();

            if (!audioFile.exists()) {
                logger.warn("Audio file not found: {}", audioPath);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(audioFile);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resourceId + ".mp3\"");

            logger.info("Streaming audio file {} to user {}", resourceId, userId);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error streaming audio file {}: {}", resourceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
