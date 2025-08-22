
package com.replit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AccessService {

    private static final Logger logger = LoggerFactory.getLogger(AccessService.class);

    @Value("${main-app.url:https://main-app.com}")
    private String mainAppUrl;

    private final RestTemplate restTemplate;

    public AccessService() {
        this.restTemplate = new RestTemplate();
    }

    public boolean checkAccess(String userId, String resourceId) {
        try {
            String url = mainAppUrl + "/api/internal/check-access?userId=" + userId + "&resourceId=" + resourceId;
            Boolean hasAccess = restTemplate.getForObject(url, Boolean.class);
            
            logger.info("Access check for user {} and resource {}: {}", userId, resourceId, hasAccess);
            return hasAccess != null && hasAccess;
        } catch (Exception e) {
            logger.error("Error checking access for user {} and resource {}: {}", userId, resourceId, e.getMessage());
            // W przypadku błędu komunikacji, domyślnie odmawiamy dostępu
            return false;
        }
    }
}
