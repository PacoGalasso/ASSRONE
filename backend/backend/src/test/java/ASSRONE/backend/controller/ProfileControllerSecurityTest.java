package ASSRONE.backend.controller;

import ASSRONE.backend.config.JwtAuthenticationEntryPoint;
import ASSRONE.backend.config.RateLimitConfig;
import ASSRONE.backend.config.SecurityConfig;
import ASSRONE.backend.dto.UserProfileDto;
import ASSRONE.backend.filter.AuthCookieOriginFilter;
import ASSRONE.backend.filter.JwtAuthFilter;
import ASSRONE.backend.filter.RateLimitFilter;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.ClientIpResolver;
import ASSRONE.backend.security.OriginValidator;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProfileController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtAuthenticationEntryPoint.class,
        RateLimitFilter.class,
        RateLimiterService.class,
        RateLimitConfig.class,
        ClientIpResolver.class,
        RefreshCookieFactory.class,
        AuthCookieOriginFilter.class,
        OriginValidator.class
})
class ProfileControllerSecurityTest {

    private static final String EMAIL = "membre@assrone.ch";
    private static final String TOKEN = "token-de-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserProfileService userProfileService;

    private static UserDetails userDetailsMock(boolean enabled) {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(EMAIL);
        when(userDetails.isEnabled()).thenReturn(enabled);
        when(userDetails.isAccountNonLocked()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();
        return userDetails;
    }

    @Test
    void jwtValideAvecCompteDesactiveRetourne401EtNAppellePasLeService() throws Exception {
        UserDetails disabledUser = userDetailsMock(false);
        when(jwtService.extractTokenType(TOKEN)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(jwtService.extractUsername(TOKEN)).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(disabledUser);
        when(jwtService.validateToken(TOKEN, disabledUser)).thenReturn(true);

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsStringIgnoringCase("désactivé"))))
                .andExpect(content().string(not(containsStringIgnoringCase("disabled"))))
                .andExpect(content().string(not(containsStringIgnoringCase("isActive"))));

        verify(userProfileService, never()).getProfile(anyString());
    }

    @Test
    void jwtValideAvecCompteActifAccedeAuProfil() throws Exception {
        UserDetails activeUser = userDetailsMock(true);
        when(jwtService.extractTokenType(TOKEN)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(jwtService.extractUsername(TOKEN)).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(activeUser);
        when(jwtService.validateToken(TOKEN, activeUser)).thenReturn(true);
        when(userProfileService.getProfile(EMAIL)).thenReturn(
                UserProfileDto.builder()
                        .id(1L)
                        .email(EMAIL)
                        .username("membre")
                        .firstName("Jean")
                        .lastName("Dupont")
                        .role("USER")
                        .build());

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.username").value("membre"));

        verify(userProfileService, times(1)).getProfile(EMAIL);
    }

    @Test
    void enregistrementServletDuFiltreEstDesactive() {
        assertThat(jwtAuthFilterRegistration.isEnabled()).isFalse();
    }
}
