package ASSRONE.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void aucunProxyConfigureIgnoreXForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "6.6.6.6");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void proxyNonApprouveEstIgnoreMemeAvecUnFauxHeader() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "6.6.6.6");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void proxyApprouveAvecUneSeuleIpDansLaChaineRetourneLIpClient() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "198.51.100.5");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.5");
    }

    @Test
    void proxyApprouveAvecChaineDePlusieursProxiesSelectionneLIpClient() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9,10.0.0.8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        // client d'origine, puis deux hops internes de confiance (le plus proche de nous
        // en dernier, a droite) : seule l'IP la plus a gauche n'est pas un proxy de confiance.
        request.addHeader("X-Forwarded-For", "198.51.100.5, 10.0.0.8, 10.0.0.9");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.5");
    }

    @Test
    void headerMalformeDeclencheUnFallbackSurRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "not-an-ip");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.9");
    }

    @Test
    void headerContenantUnHopValideDerriereUnHopMalformeDeclencheAussiUnFallback() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        // Le hop le plus a droite (le premier examine) est invalide : on n'essaie pas de
        // continuer plus a gauche, on retombe directement sur remoteAddr.
        request.addHeader("X-Forwarded-For", "198.51.100.5, garbage");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.9");
    }

    @Test
    void adresseIpv4EstSupportee() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "192.0.2.55");

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.55");
    }

    @Test
    void adresseIpv6EstSupportee() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "2001:db8::1");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void adresseIpv6CompresseeSansGroupesEstSupportee() {
        ClientIpResolver resolver = new ClientIpResolver("::1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");
        request.addHeader("X-Forwarded-For", "2001:db8::1");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void chaineEntierementComposeeDeProxiesDeConfianceRetombeSurRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9,10.0.0.8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "10.0.0.8, 10.0.0.9");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.9");
    }

    @Test
    void supportDuCidrPourLesProxiesDeConfiance() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.42");
        request.addHeader("X-Forwarded-For", "198.51.100.5");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.5");
    }

    @Test
    void ipHorsPlageCidrNEstPasUnProxyDeConfiance() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.1.5");
        request.addHeader("X-Forwarded-For", "198.51.100.5");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.1.5");
    }

    @Test
    void aucunHeaderPresentRetourneRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.9");
    }

    @Test
    void entreeInvalideDansLaConfigurationEstIgnoreeSansLeverDException() {
        ClientIpResolver resolver = new ClientIpResolver("not-an-ip, 10.0.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "198.51.100.5");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.5");
    }
}
