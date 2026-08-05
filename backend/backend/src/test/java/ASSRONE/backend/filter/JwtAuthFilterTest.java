package ASSRONE.backend.filter;

import ASSRONE.backend.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UserDetailsService userDetailsService;
    private JwtService jwtService;
    private JwtAuthFilter filter;

    @BeforeEach
    void clearBefore() {
        SecurityContextHolder.clearContext();

        userDetailsService = mock(UserDetailsService.class);

        // Service indépendant, utilisé uniquement pour fabriquer des tokens de test.
        // Découplé de userDetailsService afin que les assertions verify(...) sur ce
        // dernier ne portent que sur ce que le filtre lui-même appelle.
        UserDetailsService tokenIssuerUserDetailsService = mock(UserDetailsService.class);
        UserDetails issuerUserDetails = userDetailsMock("issuer@assrone.ch", true);
        when(tokenIssuerUserDetailsService.loadUserByUsername(anyString()))
                .thenReturn(issuerUserDetails);
        jwtService = new JwtService(tokenIssuerUserDetailsService, fakeSecret());

        filter = new JwtAuthFilter(userDetailsService, jwtService);
    }

    @AfterEach
    void clearAfter() {
        SecurityContextHolder.clearContext();
    }

    private static String fakeSecret() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static UserDetails userDetailsMock(String email, boolean enabled) {
        return userDetailsMock(email, enabled, true);
    }

    private static UserDetails userDetailsMock(String email, boolean enabled, boolean accountNonLocked) {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(email);
        when(userDetails.isEnabled()).thenReturn(enabled);
        when(userDetails.isAccountNonLocked()).thenReturn(accountNonLocked);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();
        return userDetails;
    }

    private static MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void compteActifAvecJwtValideEstAuthentifie() throws Exception {
        String email = "membre@assrone.ch";
        UserDetails user = userDetailsMock(email, true);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(user);
        String token = jwtService.generateToken(email);

        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(email);
        assertThat(authentication.getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void compteDesactiveAvecJwtValideNEstJamaisAuthentifie() throws Exception {
        String email = "desactive@assrone.ch";
        UserDetails user = userDetailsMock(email, false);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(user);
        String token = jwtService.generateToken(email);

        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void tokenInvalideNAuthentifiePas() throws Exception {
        MockHttpServletRequest request = requestWithBearer("ceci-n-est-pas-un-jwt-valide");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void contexteDejaAuthentifieNEstPasRemplace() throws Exception {
        String email = "membre@assrone.ch";
        String token = jwtService.generateToken(email);

        Authentication existing = new UsernamePasswordAuthenticationToken("deja-authentifie", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void absenceDeHeaderBearerNAuthentifiePas() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void unRefreshTokenPresenteCommeBearerNAuthentifiePasEtNAppellePasLeService() throws Exception {
        String email = "membre@assrone.ch";
        String refreshToken = jwtService.generateRefreshToken(email, "jti-de-test");

        MockHttpServletRequest request = requestWithBearer(refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void compteVerrouilleAvecJwtValideNEstJamaisAuthentifie() throws Exception {
        String email = "verrouille@assrone.ch";
        UserDetails user = userDetailsMock(email, true, false);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(user);
        String token = jwtService.generateToken(email);

        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }
}
