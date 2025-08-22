
package com.replit.controller;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"https://*.replit.com", "https://*.repl.co"})
public class TestController {

    @GetMapping("/test")
    @RateLimiter(name = "default", fallbackMethod = "rateLimitFallback")
    public Map<String, String> test(Authentication authentication) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello " + authentication.getName() + "! JWT authentication works correctly.");
        response.put("user", authentication.getName());
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return response;
    }

    public Map<String, String> rateLimitFallback(Authentication authentication, Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Rate limit exceeded");
        error.put("message", "Too many requests. Please try again later.");
        return error;
    }
}
