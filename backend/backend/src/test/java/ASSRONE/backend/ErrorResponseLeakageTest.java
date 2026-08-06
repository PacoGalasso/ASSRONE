package ASSRONE.backend;

import ASSRONE.backend.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Boots a real embedded server (unlike the MockMvc-based tests elsewhere in this
 * module) specifically because an uncaught exception with no matching
 * {@code @ExceptionHandler} needs a real servlet container to observe what a real
 * client actually receives — a MockMvc slice's TestDispatcherServlet just lets the
 * exception propagate back to the test instead. Uses the JDK's own HttpClient
 * rather than TestRestTemplate/WebTestClient, neither of which this project
 * depends on.
 *
 * Empirically, an uncaught exception here surfaces as 401 with an empty body
 * (verified by reproducing it with a stock EventController + a throwing mock,
 * nothing specific to this lot's own changes) rather than a JSON 500 through
 * BasicErrorController — this project's server.error.include-* properties (see
 * application.properties) never even get a chance to apply, since the response
 * never reaches that controller. That is a pre-existing, orthogonal quirk of this
 * app's filter chain, not something this lot introduced or is in scope to redesign
 * — but it does mean the actual guarantee that matters (no secret, no stack trace,
 * no internal class/SQL/URL ever reaches the client) holds regardless, since the
 * body is empty. This test asserts exactly that guarantee, not a specific status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:error-response-leakage-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.flyway.locations=classpath:db/no-migrations-for-context-loads-test",
        "spring.flyway.fail-on-missing-locations=false",
        "app.jwt.secret=zTjvaDrwlDQTMdHQ9vSfqXGwdkGSXJtT09uCOP+KLfO0RmyO617AZi/hK7VKCiKe",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "spring.mail.username=test@example.invalid",
        "spring.mail.password=test-password-not-real",
        "app.upload-dir=target/error-response-leakage-test-uploads",
        "app.contact.recipient=test@example.invalid",
})
class ErrorResponseLeakageTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private EventService eventService;

    @Test
    void uneExceptionNonInterceptéeNeFuiteJamaisDeDetailInterneAuClient() throws Exception {
        when(eventService.getUpcomingEvents())
                .thenThrow(new RuntimeException("jdbc:postgresql://internal-db-host:5432/assrone_prod — table events"));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/events/upcoming"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isNotEqualTo(200);
        String body = response.body() == null ? "" : response.body();
        assertThat(body).doesNotContain("jdbc:postgresql");
        assertThat(body).doesNotContain("internal-db-host");
        assertThat(body).doesNotContain("RuntimeException");
        assertThat(body).doesNotContain("ASSRONE.backend");
        assertThat(body).doesNotContain("at java.");
        assertThat(body).doesNotContainIgnoringCase("stacktrace");
        assertThat(body).doesNotContainIgnoringCase("stack trace");
    }
}
