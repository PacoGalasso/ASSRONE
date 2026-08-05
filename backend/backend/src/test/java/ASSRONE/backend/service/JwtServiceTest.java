package ASSRONE.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String fakeSecret(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static UserDetailsService userDetailsServiceReturning(UserDetails userDetails) {
        UserDetailsService service = mock(UserDetailsService.class);
        when(service.loadUserByUsername(userDetails.getUsername())).thenReturn(userDetails);
        return service;
    }

    private static UserDetails testUser(String email) {
        return new UserDetails() {
            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of(new SimpleGrantedAuthority("ROLE_USER"));
            }

            @Override
            public String getPassword() {
                return "hash";
            }

            @Override
            public String getUsername() {
                return email;
            }
        };
    }

    @Test
    void genereEtValideUnTokenAvecUnSecretDe48Octets() {
        UserDetails user = testUser("membre@assrone.ch");
        JwtService jwtService = new JwtService(userDetailsServiceReturning(user), fakeSecret(48));

        String token = jwtService.generateToken(user.getUsername());

        assertThat(jwtService.extractUsername(token)).isEqualTo("membre@assrone.ch");
        assertThat(jwtService.validateToken(token, user)).isTrue();
    }

    @Test
    void extraitCorrectementLeUsernameDuToken() {
        UserDetails user = testUser("autre-membre@assrone.ch");
        JwtService jwtService = new JwtService(userDetailsServiceReturning(user), fakeSecret(48));

        String token = jwtService.generateRefreshToken(user.getUsername(), "un-jti-de-test");

        assertThat(jwtService.extractUsername(token)).isEqualTo("autre-membre@assrone.ch");
    }

    @Test
    void unAccessTokenPorteLeTypeAccess() {
        UserDetails user = testUser("membre@assrone.ch");
        JwtService jwtService = new JwtService(userDetailsServiceReturning(user), fakeSecret(48));

        String token = jwtService.generateToken(user.getUsername());

        assertThat(jwtService.extractTokenType(token)).isEqualTo(JwtService.TOKEN_TYPE_ACCESS);
    }

    @Test
    void unRefreshTokenPorteLeTypeRefreshEtLeJtiFourni() {
        UserDetails user = testUser("membre@assrone.ch");
        JwtService jwtService = new JwtService(userDetailsServiceReturning(user), fakeSecret(48));

        String token = jwtService.generateRefreshToken(user.getUsername(), "jti-1234");

        assertThat(jwtService.extractTokenType(token)).isEqualTo(JwtService.TOKEN_TYPE_REFRESH);
        assertThat(jwtService.extractJti(token)).isEqualTo("jti-1234");
    }

    @Test
    void unAccessTokenNaPasDeJti() {
        UserDetails user = testUser("membre@assrone.ch");
        JwtService jwtService = new JwtService(userDetailsServiceReturning(user), fakeSecret(48));

        String token = jwtService.generateToken(user.getUsername());

        assertThat(jwtService.extractJti(token)).isNull();
    }

    @Test
    void deuxRefreshTokensDistinctsPortentDesJtiDistincts() {
        UserDetails user = testUser("membre@assrone.ch");
        JwtService jwtService = new JwtService(userDetailsServiceReturning(user), fakeSecret(48));

        String tokenA = jwtService.generateRefreshToken(user.getUsername(), "jti-a");
        String tokenB = jwtService.generateRefreshToken(user.getUsername(), "jti-b");

        assertThat(jwtService.extractJti(tokenA)).isEqualTo("jti-a");
        assertThat(jwtService.extractJti(tokenB)).isEqualTo("jti-b");
    }

    @Test
    void refuseUnSecretAbsent() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);

        assertThatThrownBy(() -> new JwtService(userDetailsService, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refuseUnSecretVide() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);

        assertThatThrownBy(() -> new JwtService(userDetailsService, "   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refuseUnSecretNonBase64() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        String secretInvalide = "ceci n'est pas du base64 valide !!";

        assertThatThrownBy(() -> new JwtService(userDetailsService, secretInvalide))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refuseUnSecretTropCourt() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);

        assertThatThrownBy(() -> new JwtService(userDetailsService, fakeSecret(32)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refuseUnSecretTropLong() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);

        assertThatThrownBy(() -> new JwtService(userDetailsService, fakeSecret(64)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neRevelePasLeSecretDansLeMessageDErreur() {
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        String secretTropCourt = fakeSecret(16);

        assertThatThrownBy(() -> new JwtService(userDetailsService, secretTropCourt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(secretTropCourt);
    }
}
