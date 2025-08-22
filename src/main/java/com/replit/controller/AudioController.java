
package com.replit.controller;

import com.replit.service.AccessService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"https://*.replit.com", "https://*.repl.co"})
public class AudioController {

    private static final Logger logger = LoggerFactory.getLogger(AudioController.class);
    private final AccessService accessService;

    public AudioController(AccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/audio/stream/{resourceId}")
    @RateLimiter(name = "audio-access", fallbackMethod = "rateLimitFallback")
    public ResponseEntity<Resource> streamAudioFile(
            @PathVariable String resourceId,
            Authentication authentication,
            HttpServletRequest request) {
        
        String userId = authentication.getName();
        String clientIp = getClientIpAddress(request);

        logger.info("AUDIO_STREAM_REQUEST: user={}, resource={}, ip={}", userId, resourceId, clientIp);

        try {
            // Problem 1: Validate resource ID format
            if (!isValidResourceId(resourceId)) {
                logger.error("PROBLEM_1_INVALID_RESOURCE_FORMAT: resourceId='{}' does not match pattern ^[a-zA-Z0-9_-]{{1,50}}$ or contains '..' - user={}, ip={}", 
                    resourceId, userId, clientIp);
                accessService.logUnauthorizedAccess(resourceId, clientIp, "invalid_resource_format");
                return ResponseEntity.badRequest().build();
            }

            // Problem 2: Check access permissions
            boolean hasAccess = accessService.checkAccess(userId, resourceId, clientIp);
            if (!hasAccess) {
                logger.error("PROBLEM_2_ACCESS_DENIED: Main application denied access - user={}, resource={}, ip={}", 
                    userId, resourceId, clientIp);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(null);
            }

            // Security: Prevent directory traversal
            String sanitizedResourceId = resourceId.replaceAll("[^a-zA-Z0-9_-]", "");
            Path audioFilePath = Paths.get("audio-files", sanitizedResourceId + ".mp3");
            File audioFile = audioFilePath.toFile();

            // Problem 3: Check if file exists
            if (!audioFile.exists() || !audioFile.isFile()) {
                logger.error("PROBLEM_3_FILE_NOT_FOUND: Audio file does not exist at path '{}' - user={}, resource={}, ip={}", 
                    audioFilePath.toAbsolutePath(), userId, resourceId, clientIp);
                return ResponseEntity.notFound().build();
            }

            // Prepare file for streaming
            Resource fileResource = new FileSystemResource(audioFile);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", sanitizedResourceId + ".mp3");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");

            logger.info("Streaming audio file: {} for user: {}", sanitizedResourceId, userId);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileResource);

        } catch (Exception e) {
            logger.error("PROBLEM_5_INTERNAL_ERROR: Unexpected error streaming audio file - user={}, resource={}, ip={}, error={}", 
                userId, resourceId, clientIp, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public ResponseEntity<Map<String, String>> rateLimitFallback(
            String resourceId, 
            Authentication authentication, 
            HttpServletRequest request, 
            Exception ex) {
        
        String clientIp = getClientIpAddress(request);
        String userId = authentication != null ? authentication.getName() : "unknown";
        
        logger.error("PROBLEM_4_RATE_LIMIT_EXCEEDED: Audio access rate limit exceeded (5 req/s) - user={}, resource={}, ip={}, limit=audio-access", 
            userId, resourceId, clientIp);
        
        accessService.logUnauthorizedAccess(resourceId, clientIp, "rate_limit_exceeded");
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "Rate limit exceeded");
        error.put("message", "Too many requests. Please try again later.");
        error.put("retry_after", "60");
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    private boolean isValidResourceId(String resourceId) {
        return resourceId != null && 
               resourceId.matches("^[a-zA-Z0-9_-]{1,50}$") && 
               !resourceId.contains("..");
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}

