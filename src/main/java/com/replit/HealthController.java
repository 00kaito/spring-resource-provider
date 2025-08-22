
package com.replit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Mono<String> health() {
        return Mono.just("OK");
    }
    
    @GetMapping("/")
    public Mono<String> root() {
        return Mono.just("Application is running!");
    }
}
