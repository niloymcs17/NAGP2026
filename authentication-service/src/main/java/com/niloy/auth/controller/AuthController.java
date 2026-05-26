package com.niloy.auth.controller;

import com.niloy.auth.dto.AuthResponse;
import com.niloy.auth.dto.LoginRequest;
import com.niloy.auth.model.User;
import com.niloy.auth.repository.UserRepository;
import com.niloy.auth.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getUsername(), user.getRole()));
    }
}
