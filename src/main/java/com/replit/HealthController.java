
package com.replit;

import com.replit.service.AccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final AccessService accessService;

    public HealthController(AccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("status", "UP");
        healthStatus.put("application", "audio-resource-provider");
        healthStatus.put("main_app_connectivity", accessService.isHealthy());
        healthStatus.put("circuit_breaker_failures", accessService.getFailureCount());
        healthStatus.put("timestamp", System.currentTimeMillis());
        return healthStatus;
    }
    
    @GetMapping("/")
    public String root() {
        return "Secure Audio Microservice is running! Check /health for detailed status.";
    }
}
