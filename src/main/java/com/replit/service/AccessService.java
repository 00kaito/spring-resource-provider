
package com.replit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AccessService {

    private static final Logger logger = LoggerFactory.getLogger(AccessService.class);

    @Value("${main-app.url:https://main-app.com}")
    private String mainAppUrl;
    
    @Value("${main-app.timeout:5000}")
    private int timeout;
    
    @Value("${main-app.retry-attempts:3}")
    private int maxRetryAttempts;

    private final RestTemplate restTemplate;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

    public AccessService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(timeout))
                .setReadTimeout(Duration.ofMillis(timeout))
                .build();
    }

    public boolean checkAccess(String userId, String resourceId) {
        // Circuit breaker check
        if (failureCount.get() >= CIRCUIT_BREAKER_THRESHOLD) {
            logger.warn("Circuit breaker is OPEN - denying access for user {} and resource {}", userId, resourceId);
            return false;
        }

        return checkAccessWithRetry(userId, resourceId, 0);
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
}
