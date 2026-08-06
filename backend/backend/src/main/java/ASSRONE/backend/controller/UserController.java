package ASSRONE.backend.controller;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.dto.RegisterRequest;
import ASSRONE.backend.dto.RegisterResponse;
import ASSRONE.backend.exception.EmailNotVerifiedException;
import ASSRONE.backend.exception.InvalidCredentialsException;
import ASSRONE.backend.exception.InvalidRefreshTokenException;
import ASSRONE.backend.model.AuthRequest;
import ASSRONE.backend.model.AuthResponse;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import ASSRONE.backend.security.ClientIpResolver;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.LoginAttemptService;
import ASSRONE.backend.service.RefreshTokenService;
import ASSRONE.backend.service.UserInfoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {
    private static final String REFRESH_TOKEN_MISSING_MESSAGE = "Refresh token invalide ou expiré.";

    private final UserInfoService service;
    private final UserInfoRepository userInfoRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final SecurityAuditService securityAuditService;
    private final ClientIpResolver clientIpResolver;

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
    // access token in the JSON body out, refresh token in an HttpOnly cookie).
    @PostMapping("/generateToken")
    public AuthResponse authenticateAndGetToken(@Valid @RequestBody AuthRequest authRequest,
                                                 HttpServletRequest request, HttpServletResponse response) {
        String normalizedEmail = normalizeEmail(authRequest.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, authRequest.getPassword())
            );
        } catch (AuthenticationException ex) {
            recordFailedLogin(normalizedEmail, ex);
            throw new InvalidCredentialsException("Email ou mot de passe incorrect.");
        }

        // Checked only after credentials already matched: at this point the
        // caller has already proven they know the account's password, so
        // naming the real reason here (unlike a bad password) opens no new
        // account-enumeration channel. Deliberately not routed through
        // recordFailedLogin/loginAttemptService — a correct password must
        // never count toward this account's lockout threshold.
        User user = userInfoRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Email ou mot de passe incorrect."));
        if (user.getEmailVerifiedAt() == null) {
            securityAuditService.record(SecurityEventType.LOGIN_FAILURE, SecurityEventResult.DENIED,
                    String.valueOf(user.getId()), null, "user", String.valueOf(user.getId()), "EMAIL_NOT_VERIFIED");
            throw new EmailNotVerifiedException("Veuillez vérifier votre adresse email avant de vous connecter.");
        }

        loginAttemptService.resetFailedAttempts(normalizedEmail);
        RefreshTokenService.IssuedTokens tokens = refreshTokenService.issueTokens(
                normalizedEmail, clientIpResolver.resolve(request), request.getHeader("User-Agent"));
        setRefreshCookie(response, tokens);

        securityAuditService.record(SecurityEventType.LOGIN_SUCCESS, SecurityEventResult.SUCCESS,
                String.valueOf(tokens.userId()), tokens.role(), "user", String.valueOf(tokens.userId()), null);
        return new AuthResponse(tokens.accessToken(), tokens.email(), tokens.role());
    }

    private void recordFailedLogin(String normalizedEmail, AuthenticationException ex) {
        String maskedEmail = SecurityAuditService.maskEmail(normalizedEmail);
        if (ex instanceof LockedException) {
            securityAuditService.record(SecurityEventType.LOGIN_FAILURE, SecurityEventResult.DENIED,
                    maskedEmail, null, "user", null, "ACCOUNT_LOCKED");
            return;
        }
        if (ex instanceof DisabledException) {
            securityAuditService.record(SecurityEventType.LOGIN_FAILURE, SecurityEventResult.DENIED,
                    maskedEmail, null, "user", null, "ACCOUNT_DISABLED");
            return;
        }

        loginAttemptService.registerFailedAttempt(normalizedEmail);
        if (loginAttemptService.isCurrentlyLocked(normalizedEmail)) {
            securityAuditService.record(SecurityEventType.ACCOUNT_LOCKED, SecurityEventResult.DENIED,
                    maskedEmail, null, "user", null, "THRESHOLD_REACHED");
        } else {
            securityAuditService.record(SecurityEventType.LOGIN_FAILURE, SecurityEventResult.DENIED,
                    maskedEmail, null, "user", null, "BAD_CREDENTIALS");
        }
    }

    // The refresh token is read from the HttpOnly cookie set at login/refresh
    // time, never from a JSON body — Angular has no access to its value at
    // all, so it has nothing to send here even if it tried.
    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenCookie = readRefreshCookie(request);
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            throw new InvalidRefreshTokenException(REFRESH_TOKEN_MISSING_MESSAGE);
        }

        RefreshTokenService.IssuedTokens tokens = refreshTokenService.rotate(refreshTokenCookie, clientIpResolver.resolve(request));
        setRefreshCookie(response, tokens);

        return new AuthResponse(tokens.accessToken(), tokens.email(), tokens.role());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenService.revoke(readRefreshCookie(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString());
        return ResponseEntity.noContent().build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = refreshCookieFactory.getCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setRefreshCookie(HttpServletResponse response, RefreshTokenService.IssuedTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookieFactory.issue(tokens.refreshToken(), tokens.refreshTokenMaxAge()).toString());
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
