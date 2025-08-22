
package com.replit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userId;
        final String requestURI = request.getRequestURI();
        final String clientIp = getClientIpAddress(request);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (requestURI.startsWith("/api/") && !requestURI.startsWith("/api/auth/")) {
                logger.warn("PROBLEM_JWT_MISSING_HEADER: No Authorization header for protected endpoint - uri={}, ip={}", 
                    requestURI, clientIp);
            }
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        
        try {
            userId = jwtService.extractUserId(jwt);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(jwt)) {
                    Authentication authToken = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            new ArrayList<>()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.debug("JWT_AUTH_SUCCESS: User {} authenticated successfully for {} from {}", userId, requestURI, clientIp);
                } else {
                    logger.error("PROBLEM_JWT_INVALID_TOKEN: Token validation failed - uri={}, ip={}, userId={}", 
                        requestURI, clientIp, userId);
                }
            } else if (userId == null) {
                logger.error("PROBLEM_JWT_NO_USERID: Cannot extract userId from JWT token - uri={}, ip={}", 
                    requestURI, clientIp);
            }
        } catch (Exception e) {
            logger.error("PROBLEM_JWT_PROCESSING_ERROR: Cannot process JWT token - uri={}, ip={}, error={}", 
                requestURI, clientIp, e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
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
