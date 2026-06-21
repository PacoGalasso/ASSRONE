package ASSRONE.backend.service;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
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

        private static final long TOKEN_DURATION = 1000L * 60 * 30;

        private final UserDetailsService userDetailsService;

        @Autowired
        public JwtService(UserDetailsService userDetailsService) {
            this.userDetailsService = userDetailsService;
        }

        public String generateToken(String email) {
            // Charge l'user pour obtenir son rôle
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Extrait le rôle
            String role = userDetails.getAuthorities()
                    .stream()
                    .map(auth -> auth.getAuthority())
                    .findFirst()
                    .orElse("ROLE_USER");

            // Ajoute le rôle aux claims
            Map<String, Object> claims = Map.of("role", role);

            return generateToken(claims, email);
        }

        public String generateToken(
                Map<String, Object> extraClaims,
                String email
        ) {
            Date issuedAt = new Date();
            Date expiration = new Date(
                    issuedAt.getTime() + TOKEN_DURATION
            );

            return Jwts.builder()
                    .claims(extraClaims)
                    .subject(email)
                    .issuedAt(issuedAt)
                    .expiration(expiration)
                    .signWith(getSigningKey())
                    .compact();
        }

        public String extractUsername(String token) {
            return extractClaim(token, Claims::getSubject);
        }

        public Date extractExpiration(String token) {
            return extractClaim(token, Claims::getExpiration);
        }

        public <T> T extractClaim(
                String token,
                Function<Claims, T> claimsResolver
        ) {
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

        public boolean validateToken(
                String token,
                UserDetails userDetails
        ) {
            try {
                String username = extractUsername(token);

                return username.equals(userDetails.getUsername())
                        && !isTokenExpired(token);

            } catch (JwtException | IllegalArgumentException exception) {
                return false;
            }
        }
    }