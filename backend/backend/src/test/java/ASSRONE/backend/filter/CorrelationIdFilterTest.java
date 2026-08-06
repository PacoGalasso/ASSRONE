package ASSRONE.backend.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void absenceDeHeaderGenereUnUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void headerValideEntrantEstConserve() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "req-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("req-abc-123");
    }

    @Test
    void headerInvalideEstRemplaceParUnUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "valeur avec espaces et $ymboles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(correlationId).isNotEqualTo("valeur avec espaces et $ymboles");
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void headerTropLongEstRemplaceParUnUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "a".repeat(101));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(correlationId).hasSizeLessThan(101);
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void headerDeLongueurLimiteEstConserve() throws Exception {
        String pileALaLimite = "a".repeat(100);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, pileALaLimite);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(pileALaLimite);
    }

    @Test
    void idEstPlaceDansLeMdcPendantLeTraitementDeLaRequete() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "req-mdc-test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] mdcValueDuringRequest = new String[1];
        FilterChain filterChain = (req, res) -> mdcValueDuringRequest[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

        filter.doFilter(request, response, filterChain);

        assertThat(mdcValueDuringRequest[0]).isEqualTo("req-mdc-test");
    }

    @Test
    void mdcEstNettoyeApresLaRequete() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "req-cleanup-test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mdcEstNettoyeMemeSiLeFilterChainLeveUneException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "req-exception-test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain throwingChain = (req, res) -> {
            throw new RuntimeException("panne simulee du filtre suivant");
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
                .isInstanceOf(RuntimeException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void deuxRequetesSuccessivesSansHeaderRecoiventDesIdDifferents() throws Exception {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        filter.doFilter(request1, response1, mock(FilterChain.class));

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request2, response2, mock(FilterChain.class));

        assertThat(response1.getHeader(CorrelationIdFilter.HEADER_NAME))
                .isNotEqualTo(response2.getHeader(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void headerEstPresentMemeSiLeFilterChainEcritUneReponse403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain forbiddenChain = (req, res) -> ((MockHttpServletResponse) res).setStatus(403);

        filter.doFilter(request, response, forbiddenChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
    }

    @Test
    void headerEstPresentMemeSiLeFilterChainEcritUneReponse429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain tooManyRequestsChain = (req, res) -> ((MockHttpServletResponse) res).setStatus(429);

        filter.doFilter(request, response, tooManyRequestsChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
    }

    @Test
    void headerEstPresentMemeSiLeFilterChainEcritUneReponse500() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain serverErrorChain = (req, res) -> ((MockHttpServletResponse) res).setStatus(500);

        filter.doFilter(request, response, serverErrorChain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
    }

    @Test
    void jamaisUnTokenNestUtiliseCommeCorrelationId() throws Exception {
        String faussementUnJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.signature";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, faussementUnJwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        // Un JWT contient des points, exclus du pattern autorisé : il doit
        // être rejeté et remplacé, jamais propagé tel quel comme identifiant.
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotEqualTo(faussementUnJwt);
    }
}
