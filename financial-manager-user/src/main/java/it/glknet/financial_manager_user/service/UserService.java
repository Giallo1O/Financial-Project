package it.glknet.financial_manager_user.service;

import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import it.glknet.financial_manager_user.dto.AuthResponse;
import it.glknet.financial_manager_user.dto.LoginRequest;
import it.glknet.financial_manager_user.dto.RegisterRequest;
import it.glknet.financial_manager_user.model.User;
import it.glknet.financial_manager_user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        }

        String token = UUID.randomUUID().toString();

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .token(token)
                .build();

        user = userRepository.save(user);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .userId(user.getId())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Generate a new token on each login
        String token = UUID.randomUUID().toString();
        user.setToken(token);
        userRepository.save(user);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .userId(user.getId())
                .build();
    }
}
