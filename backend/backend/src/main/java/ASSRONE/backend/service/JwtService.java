package ASSRONE.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtService {

    private static final int SIGNING_KEY_LENGTH_BYTES = 48; // HS384

    private static final long ACCESS_TOKEN_DURATION = 1000L * 60 * 30;        // 30 min
    private static final long REFRESH_TOKEN_DURATION = 1000L * 60 * 60 * 24 * 7; // 7 jours

    // Distinguishes an access token from a refresh token at the claims level:
    // the two are otherwise structurally identical JWTs signed with the same
    // key, so without this a refresh token could be presented as a Bearer
    // access token (and vice versa). Checked by JwtAuthFilter (only "access"
    // authenticates a request) and by the refresh endpoint (only "refresh" is
    // accepted there).
    public static final String TOKEN_TYPE_CLAIM = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    // Identifies which UserSession an access token was issued for. Stable
    // across every refresh-token rotation of that session, different on
    // every new login. Never replaces "sub" and grants no authority by
    // itself — JwtAuthFilter still authenticates purely on username/role;
    // this is only read where a caller needs to know "is this my current
    // session" (GET/DELETE /api/me/sessions/**).
    public static final String SESSION_ID_CLAIM = "sid";

    private final UserDetailsService userDetailsService;
    private final SecretKey signingKey;

    @Autowired
    public JwtService(UserDetailsService userDetailsService, @Value("${app.jwt.secret}") String secret) {
        this.userDetailsService = userDetailsService;
        this.signingKey = buildSigningKey(secret);
    }

    private static SecretKey buildSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret est absent : impossible de démarrer l'application.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (DecodingException | IllegalArgumentException ex) {
            throw new IllegalStateException("app.jwt.secret n'est pas une valeur Base64 valide.");
        }

        if (keyBytes.length != SIGNING_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret doit décoder vers exactement " + SIGNING_KEY_LENGTH_BYTES
                            + " octets (HS384) ; longueur obtenue : " + keyBytes.length + " octets.");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ===== ACCESS TOKEN =====
    public String generateToken(String email) {
        return generateToken(email, null);
    }

    /**
     * Same access token as {@link #generateToken(String)}, additionally
     * carrying the owning session's opaque public ID as the "sid" claim.
     * Used by RefreshTokenService, which always has a session in hand when
     * minting an access token; the sid-less overload above remains for
     * anything that only needs a bare authenticated identity.
     */
    public String generateToken(String email, String sessionId) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String role = userDetails.getAuthorities()
                .stream()
                .map(auth -> auth.getAuthority())
                .findFirst()
                .orElse("ROLE_USER");

        Map<String, Object> claims = sessionId == null
                ? Map.of("role", role, TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
                : Map.of("role", role, TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS, SESSION_ID_CLAIM, sessionId);
        return generateToken(claims, email, ACCESS_TOKEN_DURATION, null);
    }

    // ===== REFRESH TOKEN =====
    // jti is generated and persisted by RefreshTokenService (issuance/rotation/
    // revocation bookkeeping lives there, not in this purely token-mechanics
    // class), then embedded here as the JWT's standard "jti" claim so the
    // presented token and its database row can be matched back to each other.
    public String generateRefreshToken(String email, String jti) {
        Map<String, Object> claims = Map.of(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH);
        return generateToken(claims, email, REFRESH_TOKEN_DURATION, jti);
    }

    // ===== TOKEN GENERATION =====
    private String generateToken(Map<String, Object> extraClaims, String email, long duration, String jti) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + duration);

        var builder = Jwts.builder()
                .claims(extraClaims)
                .subject(email)
                .issuedAt(issuedAt)
                .expiration(expiration);
        if (jti != null) {
            builder.id(jti);
        }
        return builder.signWith(signingKey).compact();
    }

    // ===== TOKEN EXTRACTION =====
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public String extractSessionId(String token) {
        return extractClaim(token, claims -> claims.get(SESSION_ID_CLAIM, String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    // ===== CHECK IF TOKEN EXPIRED =====
    public boolean isTokenExpiringSoon(String token) {
        Date expiration = extractExpiration(token);
        long timeLeft = expiration.getTime() - System.currentTimeMillis();
        return timeLeft < (5 * 60 * 1000); // 5 min before expiration
    }
}