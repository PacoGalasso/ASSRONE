package ASSRONE.backend.config;

import ASSRONE.backend.filter.JwtAuthFilter;
import ASSRONE.backend.filter.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimitFilter rateLimitFilter, JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) {
        http
                // Still safe now that a cookie (the refresh token) is in play, not just the
                // Authorization header: /auth/refresh and /auth/logout — the only two
                // endpoints that read that cookie — are POST-only, and SameSite=Lax (see
                // RefreshCookieFactory) already withholds the cookie from cross-site POST
                // requests (Lax's top-level-navigation exemption only applies to GET). A
                // forged cross-site request to either endpoint therefore arrives with no
                // cookie at all and is rejected the same way a request with none would be —
                // no separate CSRF token adds any protection SameSite doesn't already give.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/events/*/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/events").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/events/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/events/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/membership-applications").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/membership-applications").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/membership-applications/*/accept").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/membership-applications/*/reject").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/*/role").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/documents").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/documents/*/download").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/documents").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/documents/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/committee-members/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/committee-members").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/committee-members/*/photo").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/committee-members/*/photo").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/committee-members/*/photo").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/committee-members").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/committee-members/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/committee-members/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        .requestMatchers("/auth/welcome").permitAll()
                        .requestMatchers("/auth/addNewUser").permitAll()
                        .requestMatchers("/auth/generateToken").permitAll()
                        .requestMatchers("/auth/refresh").permitAll()
                        .requestMatchers("/auth/logout").permitAll()
                        .requestMatchers("/auth/user/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .requestMatchers("/auth/admin/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(jwtAuthFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        // Never a wildcard here: the refresh-token cookie relies on
        // allowCredentials(true), and browsers refuse allowCredentials
        // together with "*" — only an explicit, known origin list works
        // with credentialed (cookie-bearing) cross-origin requests.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    // Same double-registration issue as JwtAuthFilter (see jwtAuthFilterRegistration
    // above): @Component + Filter would otherwise also auto-register into the raw
    // servlet container in addition to the explicit Spring Security chain placement.
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}