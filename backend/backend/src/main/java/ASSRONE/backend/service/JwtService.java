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
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String role = userDetails.getAuthorities()
                .stream()
                .map(auth -> auth.getAuthority())
                .findFirst()
                .orElse("ROLE_USER");

        Map<String, Object> claims = Map.of("role", role);
        return generateToken(claims, email, ACCESS_TOKEN_DURATION);
    }

    // ===== REFRESH TOKEN =====
    public String generateRefreshToken(String email) {
        return generateToken(Map.of(), email, REFRESH_TOKEN_DURATION);
    }

    // ===== TOKEN GENERATION =====
    private String generateToken(Map<String, Object> extraClaims, String email, long duration) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + duration);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(email)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    // ===== TOKEN EXTRACTION =====
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
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