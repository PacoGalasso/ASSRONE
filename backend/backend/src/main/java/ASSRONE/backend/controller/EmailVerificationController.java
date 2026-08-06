package ASSRONE.backend.controller;

import ASSRONE.backend.dto.ResendVerificationRequest;
import ASSRONE.backend.dto.VerifyEmailRequest;
import ASSRONE.backend.ratelimit.RateLimitCategory;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.EmailNormalizer;
import ASSRONE.backend.security.TokenHasher;
import ASSRONE.backend.service.EmailVerificationService;
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
 * The public email-verification endpoints. Mirrors PasswordResetController's
 * shape and anti-enumeration reasoning exactly — see there for the full
 * rationale behind the identity-keyed rate limit and the always-identical
 * resend response.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private static final String GENERIC_RESEND_RESPONSE_MESSAGE =
            "Si un compte non vérifié correspond à cette adresse, un email de vérification a été envoyé.";
    private static final String TOO_MANY_REQUESTS_MESSAGE = "Trop de requêtes. Veuillez réessayer plus tard.";
    private static final String VERIFIED_MESSAGE = "Votre adresse email a été vérifiée. Vous pouvez maintenant vous connecter.";

    private final EmailVerificationService emailVerificationService;
    private final RateLimiterService rateLimiterService;

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.getToken());
        return ResponseEntity.ok(Map.of("message", VERIFIED_MESSAGE));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.getEmail());

        if (!rateLimiterService.tryConsume(identityKey(normalizedEmail), RateLimitCategory.EMAIL_VERIFICATION_RESEND)
                .isConsumed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", TOO_MANY_REQUESTS_MESSAGE));
        }

        emailVerificationService.resend(normalizedEmail);
        return ResponseEntity.ok(Map.of("message", GENERIC_RESEND_RESPONSE_MESSAGE));
    }

    private static String identityKey(String normalizedEmail) {
        return "emailverify:" + TokenHasher.sha256Hex(normalizedEmail);
    }
}
