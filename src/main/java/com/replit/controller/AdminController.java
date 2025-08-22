
package com.replit.controller;

import com.replit.service.AccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AccessService accessService;

    public AdminController(AccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/health-check")
    public ResponseEntity<Map<String, Object>> healthCheck(Authentication authentication) {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "audio-resource-provider");
        health.put("authenticated_user", authentication.getName());
        health.put("access_service_failures", accessService.getFailureCount());
        return ResponseEntity.ok(health);
    }

    @PostMapping("/reset-circuit-breaker")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker(Authentication authentication) {
        accessService.resetCircuitBreaker();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Circuit breaker reset by " + authentication.getName());
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }
}
