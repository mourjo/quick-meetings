package me.mourjo.quickmeetings.generativetests;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.mourjo.quickmeetings.utils.RequestUtils.MEETING_CREATION_BODY_JSON;
import static me.mourjo.quickmeetings.utils.RequestUtils.MEETING_INVITE_BODY;
import static me.mourjo.quickmeetings.utils.RequestUtils.MEETING_INVITE_UPDATE_BODY_JSON;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.UserService;
import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@JqwikSpringSupport
public class RequestResponseGenTests {

    User alice;

    ObjectMapper objectMapper = JsonMapper.builder()
        .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .build();

    @Autowired
    private TestRestTemplate testRestTemplate;

    @LocalServerPort
    private int port;

    public ResponseEntity<String> postRequest(String uri, String body, HttpHeaders headers) {
        return requestBuilder(uri, body, headers).apply(HttpMethod.POST);
    }

    public ResponseEntity<String> putRequest(String uri, String body, HttpHeaders headers) {
        return requestBuilder(uri, body, headers).apply(HttpMethod.PUT);
    }

    public ResponseEntity<String> deleteRequest(String uri, String body, HttpHeaders headers) {
        return requestBuilder(uri, body, headers).apply(HttpMethod.DELETE);
    }

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.frequencyOf(
            Tuple.of(10, Arbitraries.just(alice.id())),
            Tuple.of(1, Arbitraries.longs())
        );
    }

    @Provide
    Arbitrary<String> dates() {
        return Arbitraries.of("2025-06-09", "2025-06-10", "2025-06-11", "2025-02-30", "1-2-3-4");
    }

    @Provide
    Arbitrary<String> times() {
        return Arbitraries.of("12:40", "01:10", "13:50", "23:30", "99:99", "0-9-8-7");
    }

    @Provide
    Arbitrary<String> timezones() {
        return Arbitraries.oneOf(
            Arbitraries.of("Asia/Kolkata", "Europe/Berlin"),
            Arbitraries.strings()
        );
    }

    @SneakyThrows
    void assertThatValidJson(String string, String msg) {
        assertThat(objectMapper.readValue(string, Map.class))
            .withFailMessage(msg)
            .isInstanceOf(Map.class);
    }

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    @SneakyThrows
    void responsesAreAlwaysValidJson(
        @ForAll("methods") String method,
        @ForAll("paths") String path,
        @ForAll("contentTypes") String contentTypeHeader,
        @ForAll("contentTypes") String acceptHeader,
        @ForAll("bodies") String body) {

        var response = httpRequest(method, path, acceptHeader, contentTypeHeader, body);
        var responseBody = response.getBody();
        int status = response.getStatusCode().value();

        var failureMsg = "Status: %s, Body: %s".formatted(status, responseBody);
        assertThat(responseBody).withFailMessage(failureMsg).isNotBlank();
        assertThatValidJson(responseBody, failureMsg);
        assertThat(status).withFailMessage(failureMsg).isLessThan(500);
    }

    @BeforeProperty
    @AfterProperty
    protected void setup(
        @Autowired UserService userService,
        @Autowired MeetingRepository meetingRepository,
        @Autowired UserRepository userRepository,
        @Autowired UserMeetingRepository userMeetingRepository
    ) {
        userRepository.deleteAll();
        meetingRepository.deleteAll();
        userMeetingRepository.deleteAll();
        alice = userService.createUser("alice");
    }

    @Provide
    Arbitrary<String> contentTypes() {
        return Arbitraries.of(
            MediaType.TEXT_HTML_VALUE,
            MediaType.APPLICATION_JSON_VALUE);
    }

    @Provide
    Arbitrary<String> paths() {
        return Arbitraries.of(
            "/user",
            "/meeting",
            "/meeting/invite",
            "/meeting/accept",
            "/meeting/reject",
            "/non-existent",
            "/"
        );
    }

    @Provide
    Arbitrary<String> bodies() {
        return Arbitraries.oneOf(
            meetingCreationRequest(),
            meetingInviteRequest(),
            updateInviteRequest(),
            Arbitraries.strings()
        );
    }

    @Provide
    Arbitrary<String> methods() {
        return Arbitraries.of(
            "GET", "POST", "PUT", "DELETE"
        );
    }

    Arbitrary<String> meetingCreationRequest() {
        return Combinators.combine(
                Arbitraries.of(MEETING_CREATION_BODY_JSON),
                Arbitraries.integers().between(1, 10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5),
                dates(),
                times(),
                dates(),
                times(),
                timezones()
            ).as(Tuple::of)
            .map(t ->
                t.get1().formatted(
                    t.get2(),
                    t.get3(),
                    t.get4(),
                    t.get5(),
                    t.get6(),
                    t.get7(),
                    t.get8()
                )
            );
    }

    Arbitrary<String> meetingInviteRequest() {
        return Combinators.combine(
                Arbitraries.of(MEETING_INVITE_BODY), Arbitraries.integers().greaterOrEqual(1),
                Arbitraries.integers().greaterOrEqual(1).list()
            ).as(Tuple::of)
            .map(t ->
                t.get1().formatted(
                    t.get2(),
                    "[" +
                        t.get3().stream().map(String::valueOf).collect(Collectors.joining(","))
                        + "]"
                )
            );
    }

    Arbitrary<String> updateInviteRequest() {
        return Combinators.combine(
                Arbitraries.of(MEETING_INVITE_UPDATE_BODY_JSON),
                Arbitraries.integers().greaterOrEqual(1),
                Arbitraries.integers().greaterOrEqual(1)
            ).as(Tuple::of)
            .map(t ->
                t.get1().formatted(
                    t.get2(),
                    t.get3()
                )
            );
    }

    ResponseEntity<String> httpRequest(String method, String path, String acceptHeader,
        String contentTypeHeader, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, acceptHeader);
        headers.add(HttpHeaders.CONTENT_TYPE, contentTypeHeader);

        return switch (method) {
            case "GET" -> getRequest(path, headers);
            case "POST" -> postRequest(path, body, headers);
            case "PUT" -> putRequest(path, body, headers);
            case "DELETE" -> deleteRequest(path, body, headers);
            default -> throw new IllegalArgumentException("Unexpected method");
        };
    }

    ResponseEntity<String> getRequest(String uri, HttpHeaders headers) {
        return testRestTemplate.exchange(
            uri,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );
    }

    Function<HttpMethod, ResponseEntity<String>> requestBuilder(String uri, String body,
        HttpHeaders headers) {
        return method -> testRestTemplate.exchange(
            uri,
            method,
            new HttpEntity<>(body, headers),
            String.class
        );
    }
}
