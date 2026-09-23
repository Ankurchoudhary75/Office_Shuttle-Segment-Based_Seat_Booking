package com.officeshuttle.booking.controller;

import com.officeshuttle.booking.domain.User;
import com.officeshuttle.booking.domain.UserRole;
import com.officeshuttle.booking.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        return ResponseEntity.ok(authService.login(email, password));
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody Map<String, String> request) {
        String employeeCode = request.get("employeeCode");
        String email = request.get("email");
        String password = request.get("password");
        UserRole role = UserRole.valueOf(request.getOrDefault("role", "EMPLOYEE"));

        User user = authService.registerUser(employeeCode, email, password, role);
        return ResponseEntity.ok(user);
    }
}
