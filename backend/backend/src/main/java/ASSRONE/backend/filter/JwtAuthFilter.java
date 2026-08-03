package ASSRONE.backend.filter;

import ASSRONE.backend.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Autowired
    public JwtAuthFilter(UserDetailsService userDetailsService, JwtService jwtService) {
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                username = jwtService.extractUsername(token);
            } catch (JwtException | IllegalArgumentException expiredOrInvalidToken) {
                // Token expiré ou invalide : cas normal, on laisse la requête continuer
                // sans authentification — Spring Security renverra 401/403 proprement,
                // que le front pourra intercepter pour déclencher un refresh token.
                logger.warn("[JWT DEBUG] " + request.getRequestURI() + " -> extractUsername a échoué : " + expiredOrInvalidToken.getMessage());
            }
        } else {
            logger.warn("[JWT DEBUG] " + request.getRequestURI() + " -> pas d'en-tête Authorization Bearer (header brut = " + authHeader + ")");
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                boolean valid = jwtService.validateToken(token, userDetails);
                logger.warn("[JWT DEBUG] " + request.getRequestURI() + " -> username=" + username + " userDetails.username=" + userDetails.getUsername() + " valid=" + valid);
                if (valid && userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (RuntimeException ex) {
                logger.warn("[JWT DEBUG] " + request.getRequestURI() + " -> échec loadUserByUsername/validateToken pour '" + username + "' : " + ex);
            }
        } else if (username != null) {
            logger.warn("[JWT DEBUG] " + request.getRequestURI() + " -> username=" + username + " mais un Authentication existait déjà en contexte");
        }

        filterChain.doFilter(request, response);
    }
}