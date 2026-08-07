package ASSRONE.backend.controller;

import ASSRONE.backend.dto.ForgotPasswordRequest;
import ASSRONE.backend.dto.ResetPasswordRequest;
import ASSRONE.backend.ratelimit.RateLimitCategory;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.EmailNormalizer;
import ASSRONE.backend.security.TokenHasher;
import ASSRONE.backend.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The public "forgot password" / "reset password" endpoints. Both are
 * deliberately thin: every actual decision (does this account exist, is
 * this token still valid) is made inside PasswordResetService, never here —
 * this class only owns translating that into an HTTP response, and the one
 * thing it must never do is let the response for forgotPassword differ
 * based on what the service found.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private static final String GENERIC_RESPONSE_MESSAGE =
            "Si un compte correspond à cette adresse, un email de réinitialisation a été envoyé.";
    private static final String TOO_MANY_REQUESTS_MESSAGE = "Trop de requêtes. Veuillez réessayer plus tard.";
    private static final String RESET_SUCCESS_MESSAGE =
            "Votre mot de passe a été réinitialisé. Veuillez vous reconnecter.";

    private final PasswordResetService passwordResetService;
    private final RateLimiterService rateLimiterService;

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.getEmail());

        // Complements RateLimitFilter's per-IP bucket already registered for this
        // route (see RateLimitedRoutes): this one is keyed by a one-way hash of the
        // normalized identity, so a request spread across many IPs but targeting the
        // same address is still capped. The key depends only on the submitted email
        // string, never on whether an account actually exists for it, so refusing
        // here reveals nothing about account existence either.
        if (!rateLimiterService.tryConsume(identityKey(normalizedEmail), RateLimitCategory.PASSWORD_RESET_REQUEST)
                .isConsumed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", TOO_MANY_REQUESTS_MESSAGE));
        }

        passwordResetService.requestReset(normalizedEmail);
        return ResponseEntity.ok(Map.of("message", GENERIC_RESPONSE_MESSAGE));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", RESET_SUCCESS_MESSAGE));
    }

    private static String identityKey(String normalizedEmail) {
        return "pwreset:" + TokenHasher.sha256Hex(normalizedEmail);
    }
}
