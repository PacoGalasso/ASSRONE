package ASSRONE.backend.controller;

import ASSRONE.backend.config.JwtAuthenticationEntryPoint;
import ASSRONE.backend.config.SecurityConfig;
import ASSRONE.backend.filter.JwtAuthFilter;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProfileController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtAuthenticationEntryPoint.class
})
class ProfileControllerSecurityTest {

    private static final String EMAIL = "membre@assrone.ch";
    private static final String TOKEN = "token-de-test";

    @Autowired
    private MockMvc mockMvc;

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
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();
        return userDetails;
    }

    @Test
    void jwtValideAvecCompteDesactiveRetourne401EtNAppellePasLeService() throws Exception {
        UserDetails disabledUser = userDetailsMock(false);
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
}
