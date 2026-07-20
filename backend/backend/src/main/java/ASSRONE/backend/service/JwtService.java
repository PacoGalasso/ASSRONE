package ASSRONE.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtService {

    private static final String SECRET =
            "5367566859703373367639792F423F452848284D6251655468576D5A71347437";

    private static final long ACCESS_TOKEN_DURATION = 1000L * 60 * 30;        // 30 min
    private static final long REFRESH_TOKEN_DURATION = 1000L * 60 * 60 * 24 * 7; // 7 jours

    private final UserDetailsService userDetailsService;

    @Autowired
    public JwtService(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
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
                .signWith(getSigningKey())
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
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
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