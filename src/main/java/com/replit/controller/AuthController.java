
package com.replit.controller;

import com.replit.dto.AuthRequest;
import com.replit.dto.AuthResponse;
import com.replit.security.JwtService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"https://*.replit.com", "https://*.repl.co"})
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @RateLimiter(name = "default", fallbackMethod = "loginRateLimitFallback")
    public ResponseEntity<AuthResponse> authenticate(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIpAddress(httpRequest);
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            String token = jwtService.generateToken(request.getUsername());
            logger.info("User {} authenticated successfully from IP {}", request.getUsername(), clientIp);
            
            return ResponseEntity.ok(new AuthResponse(token));
        } catch (BadCredentialsException e) {
            logger.warn("Authentication failed for user: {} from IP {}", request.getUsername(), clientIp);
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    public ResponseEntity<Map<String, String>> loginRateLimitFallback(
            AuthRequest request, 
            HttpServletRequest httpRequest, 
            Exception ex) {
        
        String clientIp = getClientIpAddress(httpRequest);
        logger.warn("Rate limit exceeded for login attempt from IP: {}", clientIp);
        
        Map<String, String> error = new HashMap<>();
        error.put("error", "Rate limit exceeded");
        error.put("message", "Too many login attempts. Please try again later.");
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
