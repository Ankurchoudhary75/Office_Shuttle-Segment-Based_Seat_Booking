package com.officeshuttle.booking.service;

import com.officeshuttle.booking.config.JwtService;
import com.officeshuttle.booking.domain.User;
import com.officeshuttle.booking.domain.UserRole;
import com.officeshuttle.booking.exception.UnauthorizedActionException;
import com.officeshuttle.booking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<String, Object> login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedActionException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedActionException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", token);
        response.put("tokenType", "Bearer");
        response.put("userId", user.getUserId());
        response.put("email", user.getEmail());
        response.put("employeeCode", user.getEmployeeCode());
        response.put("role", user.getRole().name());

        return response;
    }

    @Transactional
    public User registerUser(String employeeCode, String email, String rawPassword, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        if (userRepository.existsByEmployeeCode(employeeCode)) {
            throw new IllegalArgumentException("Employee code already registered: " + employeeCode);
        }

        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(employeeCode, email, hash, role);
        return userRepository.save(user);
    }
}
