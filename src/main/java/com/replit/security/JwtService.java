
package com.replit.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;
    
    @Value("${jwt.issuer}")
    private String expectedIssuer;
    
    @Value("${jwt.audience}")
    private String expectedAudience;

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(String userId) {
        return generateToken(userId, jwtExpiration);
    }

    public String generateToken(String userId, Long expiration) {
        return Jwts
                .builder()
                .setSubject(userId)
                .setIssuer(expectedIssuer)
                .setAudience(expectedAudience)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, String userId) {
        final String tokenUserId = extractUserId(token);
        return (tokenUserId.equals(userId)) && !isTokenExpired(token);
    }

    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token) && isIssuerValid(token) && isAudienceValid(token);
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    public boolean isIssuerValid(String token) {
        try {
            String issuer = extractClaim(token, Claims::getIssuer);
            return expectedIssuer.equals(issuer);
        } catch (Exception e) {
            logger.warn("Invalid issuer in token: {}", e.getMessage());
            return false;
        }
    }
    
    public boolean isAudienceValid(String token) {
        try {
            String audience = extractClaim(token, Claims::getAudience);
            return expectedAudience.equals(audience);
        } catch (Exception e) {
            logger.warn("Invalid audience in token: {}", e.getMessage());
            return false;
        }
    }
    
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }
    
    public String extractPermissions(String token) {
        return extractClaim(token, claims -> claims.get("permissions", String.class));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts
                    .parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token");
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
            throw new RuntimeException("JWT token is expired");
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
            throw new RuntimeException("JWT token is unsupported");
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
            throw new RuntimeException("JWT claims string is empty");
        }
    }

    private Key getSignInKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            // Fallback: generate a secure key if the provided secret is invalid
            logger.warn("Invalid JWT secret, generating secure key");
            return Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
    }
}
