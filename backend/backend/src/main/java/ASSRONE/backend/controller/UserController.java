package ASSRONE.backend.controller;

import ASSRONE.backend.dto.RefreshTokenRequest;
import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.dto.RegisterResponse;
import ASSRONE.backend.exception.InvalidCredentialsException;
import ASSRONE.backend.model.AuthRequest;
import ASSRONE.backend.model.AuthResponse;
import ASSRONE.backend.service.LoginAttemptService;
import ASSRONE.backend.service.RefreshTokenService;
import ASSRONE.backend.service.UserInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserInfoService service;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;

    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome this endpoint is not secure";
    }

    @PostMapping("/addNewUser")
    public RegisterResponse addNewUser(@Valid @RequestBody RegisterRequest request) {
        return service.addUser(request);
    }

    // Historical path name — kept as-is to avoid breaking the existing
    // frontend contract; this is the real login endpoint (email + password in,
    // access + refresh token pair out).
    @PostMapping("/generateToken")
    public AuthResponse authenticateAndGetToken(@RequestBody AuthRequest authRequest) {
        String normalizedEmail = normalizeEmail(authRequest.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, authRequest.getPassword())
            );
        } catch (AuthenticationException ex) {
            loginAttemptService.registerFailedAttempt(normalizedEmail);
            throw new InvalidCredentialsException("Email ou mot de passe incorrect.");
        }

        loginAttemptService.resetFailedAttempts(normalizedEmail);
        RefreshTokenService.IssuedTokens tokens = refreshTokenService.issueTokens(normalizedEmail);

        return new AuthResponse(tokens.accessToken(), tokens.email(), tokens.role(), tokens.refreshToken());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenService.IssuedTokens tokens = refreshTokenService.rotate(request.getRefreshToken());
        return new AuthResponse(tokens.accessToken(), tokens.email(), tokens.role(), tokens.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null) {
            refreshTokenService.revoke(request.getRefreshToken());
        }
        return ResponseEntity.noContent().build();
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
