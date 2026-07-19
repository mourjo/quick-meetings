package me.mourjo.quickmeetings;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * Property-based test that verifies the HTTP layer always returns JSON responses, regardless of the request body, path, method, or content type sent by the
 * client.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@JqwikSpringSupport
class AlwaysJsonResponsePropertyTest {

    /**
     * Known endpoints in the application.
     */
    private static final String[] KNOWN_PATHS = {
        "/user",
        "/meeting",
        "/meeting/invite",
        "/meeting/accept",
        "/meeting/reject"
    };
    /**
     * HTTP methods to exercise.
     */
    private static final HttpMethod[] HTTP_METHODS = {
        HttpMethod.GET,
        HttpMethod.POST,
        HttpMethod.PUT,
        HttpMethod.DELETE,
        HttpMethod.PATCH
    };
    @Autowired
    TestRestTemplate restTemplate;

    // -- Providers --

    @Provide
    Arbitrary<String> requestBodies() {
        return Arbitraries.oneOf(
            // Completely arbitrary strings (could be XML, plain text, garbage)
            Arbitraries.strings().ofMinLength(0).ofMaxLength(500),
            // Strings that look like partial/broken JSON
            Arbitraries.of("{", "}", "{\"a\":", "[", "]", "null", "true", "123",
                "{\"userId\":", "{\"meetingId\":}", "{{}}"),
            // Valid-ish JSON with arbitrary field values
            Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(20),
                Arbitraries.longs()
            ).as((name, id) -> "{\"userId\":%d,\"name\":\"%s\"}".formatted(id, name)),
            // XML-like content
            Arbitraries.of("<xml>test</xml>", "<meeting><id>1</id></meeting>"),
            // Empty body
            Arbitraries.just("")
        );
    }

    @Provide
    Arbitrary<String> requestPaths() {
        return Arbitraries.oneOf(
            // Known endpoints
            Arbitraries.of(KNOWN_PATHS),
            // Random unknown paths
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
                .map(s -> "/" + s),
            // Paths with special characters
            Arbitraries.of("/../../etc/passwd", "/<script>", "/meeting/../secret",
                "/user?name=test", "/meeting/999/nonexistent")
        );
    }

    @Provide
    Arbitrary<HttpMethod> httpMethods() {
        return Arbitraries.of(HTTP_METHODS);
    }

    @Provide
    Arbitrary<String> contentTypes() {
        return Arbitraries.of(
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.TEXT_HTML_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            "text/csv",
            "application/octet-stream",
            "invalid/content-type"
        );
    }

    // -- Properties --

    @Property(tries = 500)
    void responseIsAlwaysJson(
        @ForAll("requestBodies") String body,
        @ForAll("requestPaths") String path,
        @ForAll("httpMethods") HttpMethod method,
        @ForAll("contentTypes") String contentType
    ) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        var request = new HttpEntity<>(body, headers);

        var response = restTemplate.exchange(path, method, request, String.class);

        assertThat(response.getHeaders().getContentType())
            .as("Response to %s %s with Content-Type '%s' and body '%s' must be JSON "
                    + "(status=%d)",
                method, path, contentType,
                body.length() > 80 ? body.substring(0, 80) + "..." : body,
                response.getStatusCode().value())
            .isNotNull()
            .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.APPLICATION_JSON))
                .as("Expected application/json compatible but got '%s'", ct)
                .isTrue());
    }

    @Property(tries = 200)
    void knownEndpointsWithArbitraryJsonAlwaysReturnJson(
        @ForAll("requestBodies") String body
    ) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new HttpEntity<>(body, headers);

        for (String path : KNOWN_PATHS) {
            var response = restTemplate.exchange(path, HttpMethod.POST, request,
                String.class);

            assertThat(response.getHeaders().getContentType())
                .as("POST %s with body '%s' must return JSON (status=%d)",
                    path,
                    body.length() > 80 ? body.substring(0, 80) + "..." : body,
                    response.getStatusCode().value())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.APPLICATION_JSON))
                    .as("Expected application/json compatible but got '%s'", ct)
                    .isTrue());
        }
    }
}
