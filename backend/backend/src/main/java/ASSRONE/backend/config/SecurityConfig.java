package ASSRONE.backend.config;

import ASSRONE.backend.filter.AuthCookieOriginFilter;
import ASSRONE.backend.filter.CorrelationIdFilter;
import ASSRONE.backend.filter.JwtAuthFilter;
import ASSRONE.backend.filter.RateLimitFilter;
import ASSRONE.backend.security.ContentSecurityPolicy;
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
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // No feature in this list is used anywhere in the frontend (verified during
    // the audit for this lot: no camera/microphone/geolocation/payment API call,
    // no clipboard/USB/Bluetooth/sensor access, no fullscreen or autoplay usage)
    // — every one is explicitly denied rather than left to the browser's default.
    // Package-private (not private) so a test can assert deployment/nginx/assrone.conf.example's
    // Permissions-Policy value never silently drifts from this one.
    static final String PERMISSIONS_POLICY = String.join(", ",
            "accelerometer=()", "autoplay=()", "bluetooth=()", "camera=()",
            "clipboard-read=()", "clipboard-write=()", "fullscreen=()", "geolocation=()",
            "gyroscope=()", "magnetometer=()", "microphone=()", "payment=()",
            "picture-in-picture=()", "usb=()"
    );

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final AuthCookieOriginFilter authCookieOriginFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimitFilter rateLimitFilter,
                           AuthCookieOriginFilter authCookieOriginFilter, CorrelationIdFilter correlationIdFilter,
                           JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.authCookieOriginFilter = authCookieOriginFilter;
        this.correlationIdFilter = correlationIdFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
            @Value("${app.security.csp.upgrade-insecure-requests:false}") boolean upgradeInsecureRequests) {
        http
                // /auth/refresh and /auth/logout — the only two endpoints that read the
                // refresh cookie automatically — are POST-only, and SameSite=Lax (see
                // RefreshCookieFactory) already withholds the cookie from cross-site POST
                // requests (Lax's top-level-navigation exemption only applies to GET). Spring's
                // built-in CSRF token mechanism would add nothing SameSite doesn't already give
                // here, and would break the stateless Bearer-token endpoints for no benefit
                // (they never accept cookie-based authentication at all — see JwtAuthFilter).
                // AuthCookieOriginFilter below is the defense-in-depth layer for the two
                // cookie-authenticated endpoints: a strict Origin/Referer check that doesn't
                // depend on browser SameSite enforcement and also catches same-site-but-
                // cross-origin requests (e.g. another subdomain) that SameSite cannot.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // X-Content-Type-Options: nosniff and X-Frame-Options: DENY are already
                // Spring Security's own defaults (verified empirically — no defaultsDisabled()
                // call here, so they stay on) and are not repeated below. Everything below is
                // genuinely absent otherwise.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(ContentSecurityPolicy.directives(upgradeInsecureRequests)))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN))
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
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
                        .requestMatchers(HttpMethod.GET, "/api/me/sessions").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/me/sessions/others").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/me/sessions/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/me/sessions").authenticated()
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
                // Chained relative to each other, anchored at the front to HeaderWriterFilter
                // rather than to SecurityContextHolderFilter as before: AuthCookieOriginFilter
                // and RateLimitFilter both write a response directly (403/429) without going
                // through Spring Security's AuthenticationEntryPoint/AccessDeniedHandler
                // machinery, and HeaderWriterFilter only covers a response if it runs before
                // whatever writes to it. Without this explicit anchor, a rejection from either
                // filter left every security header (CSP, nosniff, etc.) off the response —
                // confirmed by a failing test before this line was added. SecurityContextHolderFilter
                // itself still runs before HeaderWriterFilter in Spring Security's own default
                // ordering, so JwtAuthFilter — last in this chain — still runs after it too.
                // CorrelationIdFilter runs before everything else in this custom chain
                // (even HeaderWriterFilter): it sets its response header and populates
                // MDC directly and immediately, not through HeaderWriterFilter's lazy-
                // write wrapper, so its own ordering constraint is simpler — it just
                // needs to run before any filter that might short-circuit with a direct
                // response write (AuthCookieOriginFilter's 403, RateLimitFilter's 429),
                // so those rejections still carry a correlation ID and so any
                // SecurityAuditService call made while handling this request already has
                // one in MDC.
                .addFilterBefore(correlationIdFilter, HeaderWriterFilter.class)
                .addFilterAfter(authCookieOriginFilter, HeaderWriterFilter.class)
                .addFilterAfter(rateLimitFilter, AuthCookieOriginFilter.class)
                .addFilterAfter(jwtAuthFilter, RateLimitFilter.class);

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

    // Same double-registration issue as JwtAuthFilter/RateLimitFilter above.
    @Bean
    public FilterRegistrationBean<AuthCookieOriginFilter> authCookieOriginFilterRegistration(AuthCookieOriginFilter authCookieOriginFilter) {
        FilterRegistrationBean<AuthCookieOriginFilter> registration = new FilterRegistrationBean<>(authCookieOriginFilter);
        registration.setEnabled(false);
        return registration;
    }

    // Same double-registration issue as the other filters above.
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(CorrelationIdFilter correlationIdFilter) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(correlationIdFilter);
        registration.setEnabled(false);
        return registration;
    }
}