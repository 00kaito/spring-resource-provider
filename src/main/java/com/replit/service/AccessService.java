
package com.replit.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AccessService {

    private static final Logger logger = LoggerFactory.getLogger(AccessService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    @Value("${main-app.url:https://main-app.com}")
    private String mainAppUrl;
    
    @Value("${main-app.timeout:5000}")
    private int timeout;
    
    @Value("${main-app.retry-attempts:3}")
    private int maxRetryAttempts;

    private RestTemplate restTemplate;
    private final RestTemplateBuilder restTemplateBuilder;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

    // Metrics
    private final Counter accessRequestCounter;
    private final Counter accessGrantedCounter;
    private final Counter accessDeniedCounter;
    private final Counter unauthorizedAccessCounter;
    private final Timer accessCheckTimer;

    public AccessService(RestTemplateBuilder builder, MeterRegistry meterRegistry) {
        this.restTemplateBuilder = builder;
        this.accessRequestCounter = Counter.builder("access_requests_total")
                .description("Total number of access requests")
                .register(meterRegistry);
        this.accessGrantedCounter = Counter.builder("access_granted_total")
                .description("Total number of granted access requests")
                .register(meterRegistry);
        this.accessDeniedCounter = Counter.builder("access_denied_total")
                .description("Total number of denied access requests")
                .register(meterRegistry);
        this.unauthorizedAccessCounter = Counter.builder("unauthorized_access_attempts_total")
                .description("Total number of unauthorized access attempts")
                .register(meterRegistry);
        this.accessCheckTimer = Timer.builder("access_check_duration")
                .description("Time spent checking access permissions")
                .register(meterRegistry);
    }

    @PostConstruct
    private void initializeRestTemplate() {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeout))
                .setReadTimeout(Duration.ofMillis(timeout))
                .build();
    }

    public boolean checkAccess(String userId, String resourceId) {
        return checkAccess(userId, resourceId, null);
    }

    public boolean checkAccess(String userId, String resourceId, String clientIp) {
        accessRequestCounter.increment();
        
        // Setup MDC for structured logging
        MDC.put("userId", userId);
        MDC.put("resourceId", resourceId);
        MDC.put("clientIp", clientIp != null ? clientIp : "unknown");
        MDC.put("timestamp", LocalDateTime.now().toString());

        try {
            Timer.Sample sample = Timer.start();
            
            // Circuit breaker check
            if (failureCount.get() >= CIRCUIT_BREAKER_THRESHOLD) {
                logger.warn("Circuit breaker is OPEN - denying access for user {} and resource {}", userId, resourceId);
                auditLogger.warn("ACCESS_DENIED_CIRCUIT_BREAKER: user={}, resource={}, ip={}, reason=circuit_breaker_open", 
                    userId, resourceId, clientIp);
                accessDeniedCounter.increment();
                return false;
            }

            boolean hasAccess = checkAccessWithRetry(userId, resourceId, 0);
            sample.stop(accessCheckTimer);

            // Audit logging
            if (hasAccess) {
                auditLogger.info("ACCESS_GRANTED: user={}, resource={}, ip={}, timestamp={}", 
                    userId, resourceId, clientIp, LocalDateTime.now());
                accessGrantedCounter.increment();
            } else {
                auditLogger.warn("ACCESS_DENIED: user={}, resource={}, ip={}, timestamp={}", 
                    userId, resourceId, clientIp, LocalDateTime.now());
                accessDeniedCounter.increment();
            }

            return hasAccess;
        } finally {
            MDC.clear();
        }
    }
    
    private boolean checkAccessWithRetry(String userId, String resourceId, int attempt) {
        try {
            String url = mainAppUrl + "/api/internal/check-access?userId=" + userId + "&resourceId=" + resourceId;
            ResponseEntity<Boolean> response = restTemplate.getForEntity(url, Boolean.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                boolean hasAccess = response.getBody();
                logger.info("Access check for user {} and resource {}: {}", userId, resourceId, hasAccess);
                
                // Reset failure count on successful call
                failureCount.set(0);
                return hasAccess;
            } else {
                logger.warn("Invalid response from main app for user {} and resource {}", userId, resourceId);
                return false;
            }
        } catch (Exception e) {
            failureCount.incrementAndGet();
            logger.error("Error checking access for user {} and resource {} (attempt {}): {}", 
                    userId, resourceId, attempt + 1, e.getMessage());
            
            if (attempt < maxRetryAttempts - 1) {
                try {
                    Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                    return checkAccessWithRetry(userId, resourceId, attempt + 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }
    
    public int getFailureCount() {
        return failureCount.get();
    }
    
    public void resetCircuitBreaker() {
        failureCount.set(0);
        logger.info("Circuit breaker reset");
    }

    @Async
    public CompletableFuture<Boolean> checkAccessAsync(String userId, String resourceId, String clientIp) {
        return CompletableFuture.completedFuture(checkAccess(userId, resourceId, clientIp));
    }

    public void logUnauthorizedAccess(String resourceId, String clientIp, String reason) {
        unauthorizedAccessCounter.increment();
        MDC.put("resourceId", resourceId);
        MDC.put("clientIp", clientIp != null ? clientIp : "unknown");
        MDC.put("reason", reason);
        MDC.put("timestamp", LocalDateTime.now().toString());
        
        try {
            auditLogger.warn("UNAUTHORIZED_ACCESS_ATTEMPT: resource={}, ip={}, reason={}, timestamp={}", 
                resourceId, clientIp, reason, LocalDateTime.now());
            logger.warn("Unauthorized access attempt to resource {} from IP {} - reason: {}", 
                resourceId, clientIp, reason);
        } finally {
            MDC.clear();
        }
    }

    public boolean isHealthy() {
        try {
            String healthUrl = mainAppUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            logger.warn("Health check failed for main application: {}", e.getMessage());
            return false;
        }
    }
}
