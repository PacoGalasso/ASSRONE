package ASSRONE.backend.controller;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.config.JwtAuthenticationEntryPoint;
import ASSRONE.backend.config.RateLimitConfig;
import ASSRONE.backend.config.SecurityConfig;
import ASSRONE.backend.exception.LastAdministratorException;
import ASSRONE.backend.exception.SelfActionForbiddenException;
import ASSRONE.backend.exception.UserNotFoundException;
import ASSRONE.backend.filter.AuthCookieOriginFilter;
import ASSRONE.backend.filter.CorrelationIdFilter;
import ASSRONE.backend.filter.JwtAuthFilter;
import ASSRONE.backend.filter.RateLimitFilter;
import ASSRONE.backend.model.UserRole;
import ASSRONE.backend.ratelimit.RateLimiterService;
import ASSRONE.backend.security.ClientIpResolver;
import ASSRONE.backend.security.OriginValidator;
import ASSRONE.backend.security.RefreshCookieFactory;
import ASSRONE.backend.service.AdminUserManagementService;
import ASSRONE.backend.service.JwtService;
import ASSRONE.backend.service.UserInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
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
        OriginValidator.class,
        CorrelationIdFilter.class,
        SecurityAuditService.class
})
class AdminUserControllerSecurityTest {

    private static final String ADMIN_EMAIL = "admin@assrone.ch";
    private static final String USER_EMAIL = "membre@assrone.ch";
    private static final String TOKEN = "token-de-test";

    @Autowired
    private MockMvc mockMvc;

    // UserInfoService implements UserDetailsService: a single mock here satisfies
    // both AdminUserController's own dependency and JwtAuthFilter's UserDetailsService
    // autowiring. Mocking UserDetailsService separately would register two
    // UserDetailsService-typed candidates and make JwtAuthFilter's autowiring ambiguous.
    @MockitoBean
    private UserInfoService userInfoService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AdminUserManagementService adminUserManagementService;

    private void authenticateAs(String email, String role) {
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn(email);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .when(principal).getAuthorities();
        when(jwtService.extractTokenType(TOKEN)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(jwtService.extractUsername(TOKEN)).thenReturn(email);
        when(userInfoService.loadUserByUsername(email)).thenReturn(principal);
        when(jwtService.validateToken(TOKEN, principal)).thenReturn(true);
    }

    @Test
    void utilisateurNonAdministrateurRecoit403SurChangementDeRole() throws Exception {
        authenticateAs(USER_EMAIL, "USER");

        mockMvc.perform(put("/api/users/2/role")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void utilisateurNonAdministrateurRecoit403SurSuppression() throws Exception {
        authenticateAs(USER_EMAIL, "USER");

        mockMvc.perform(delete("/api/users/2")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrateurPeutModifierLeRoleDUnAutreUtilisateur() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doNothing().when(adminUserManagementService).changeRole(ADMIN_EMAIL, 2L, UserRole.ADMIN);

        mockMvc.perform(put("/api/users/2/role")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNoContent());

        verify(adminUserManagementService).changeRole(eq(ADMIN_EMAIL), eq(2L), eq(UserRole.ADMIN));
    }

    @Test
    void roleInconnuDansLeCorpsEstRefuse() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");

        mockMvc.perform(put("/api/users/2/role")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"role\":\"SUPERADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void utilisateurCibleInexistantRetourne404() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doThrow(new UserNotFoundException("Utilisateur introuvable : 99"))
                .when(adminUserManagementService).changeRole(ADMIN_EMAIL, 99L, UserRole.ADMIN);

        mockMvc.perform(put("/api/users/99/role")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modificationDeSonPropreRoleRetourne403() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doThrow(new SelfActionForbiddenException("Un administrateur ne peut pas modifier son propre rôle."))
                .when(adminUserManagementService).changeRole(ADMIN_EMAIL, 1L, UserRole.USER);

        mockMvc.perform(put("/api/users/1/role")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void retrogradationDuDernierAdministrateurRetourne409() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doThrow(new LastAdministratorException("Impossible de rétrograder le dernier administrateur."))
                .when(adminUserManagementService).changeRole(ADMIN_EMAIL, 2L, UserRole.USER);

        mockMvc.perform(put("/api/users/2/role")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void administrateurPeutSupprimerUnUtilisateur() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doNothing().when(adminUserManagementService).deleteUser(ADMIN_EMAIL, 2L);

        mockMvc.perform(delete("/api/users/2")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent());

        verify(adminUserManagementService).deleteUser(eq(ADMIN_EMAIL), eq(2L));
    }

    @Test
    void suppressionDUnUtilisateurInexistantRetourne404() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doThrow(new UserNotFoundException("Utilisateur introuvable : 99"))
                .when(adminUserManagementService).deleteUser(ADMIN_EMAIL, 99L);

        mockMvc.perform(delete("/api/users/99")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void suppressionDeSonProprePompteRetourne403() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doThrow(new SelfActionForbiddenException("Un administrateur ne peut pas supprimer son propre compte."))
                .when(adminUserManagementService).deleteUser(ADMIN_EMAIL, 1L);

        mockMvc.perform(delete("/api/users/1")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void suppressionDuDernierAdministrateurRetourne409() throws Exception {
        authenticateAs(ADMIN_EMAIL, "ADMIN");
        doThrow(new LastAdministratorException("Impossible de supprimer le dernier administrateur."))
                .when(adminUserManagementService).deleteUser(ADMIN_EMAIL, 2L);

        mockMvc.perform(delete("/api/users/2")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isConflict());
    }
}
