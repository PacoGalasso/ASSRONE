package ASSRONE.backend.controller;

import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.exception.InvalidCredentialsException;
import ASSRONE.backend.model.AuthRequest;
import ASSRONE.backend.model.AuthResponse;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.LoginAttemptService;
import ASSRONE.backend.service.UserInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserInfoService service;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;

    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome this endpoint is not secure";
    }

    @PostMapping("/addNewUser")
    public String addNewUser(@Valid @RequestBody RegisterRequest request) {
        return service.addUser(request);
    }

    @PostMapping("/generateToken")
    public AuthResponse authenticateAndGetToken(@RequestBody AuthRequest authRequest) {
        String normalizedEmail = normalizeEmail(authRequest.getEmail());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, authRequest.getPassword())
            );
        } catch (AuthenticationException ex) {
            loginAttemptService.registerFailedAttempt(normalizedEmail);
            throw new InvalidCredentialsException("Email ou mot de passe incorrect.");
        }
        if (authentication.isAuthenticated()) {
            loginAttemptService.resetFailedAttempts(normalizedEmail);

            String accessToken = jwtService.generateToken(normalizedEmail);
            String refreshToken = jwtService.generateRefreshToken(normalizedEmail);

            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .orElse("ROLE_USER");

            return new AuthResponse(
                    accessToken,
                    normalizedEmail,
                    role,
                    refreshToken
            );
        } else {
            throw new UsernameNotFoundException("Invalid user request!");
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @GetMapping("/user/profile")
    public String userProfile() {
        return "User Profile - Only accessible to ROLE_USER";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "Admin Dashboard - Only accessible to ROLE_ADMIN";
    }
}
