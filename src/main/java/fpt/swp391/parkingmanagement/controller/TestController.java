package fpt.swp391.parkingmanagement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/auth-check")
    public ResponseEntity<Map<String, Object>> checkAuth(Authentication authentication, Principal principal) {
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", authentication != null && authentication.isAuthenticated());
        response.put("name", principal != null ? principal.getName() : null);
        response.put("authorities", authentication != null ? authentication.getAuthorities() : null);
        response.put("details", authentication != null ? authentication.getDetails() : null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/public")
    public ResponseEntity<String> publicEndpoint() {
        return ResponseEntity.ok("Public endpoint - no auth required");
    }

    @GetMapping("/admin-check")
    public ResponseEntity<String> adminCheck() {
        return ResponseEntity.ok("Admin access granted");
    }
}