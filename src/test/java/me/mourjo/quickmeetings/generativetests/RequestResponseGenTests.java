package me.mourjo.quickmeetings.generativetests;

import static me.mourjo.quickmeetings.utils.RequestUtils.MEETING_CREATION_BODY_JSON;
import static me.mourjo.quickmeetings.utils.RequestUtils.MEETING_INVITE_BODY;
import static me.mourjo.quickmeetings.utils.RequestUtils.MEETING_INVITE_UPDATE_BODY_JSON;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.ZoneId;
import java.util.Map;
import java.util.UnknownFormatConversionException;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.UserService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@JqwikSpringSupport
public class RequestResponseGenTests {

    @Autowired
    MockMvc mockMvc;


    User alice;
    ObjectMapper om = JsonMapper.builder()
        .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .build();

    private static MockHttpServletRequestBuilder buildRequest(String path,
        String contentType, String name, long userId, String fromDate, String fromTime,
        String toDate, String toTime,
        String timezone, String method, String body) {
        MockHttpServletRequestBuilder req = switch (method) {
            case "GET" -> MockMvcRequestBuilders.get(path);
            case "POST" -> MockMvcRequestBuilders.post(path);
            case "PUT" -> MockMvcRequestBuilders.put(path);
            case "DELETE" -> MockMvcRequestBuilders.delete(path);
            default -> throw new IllegalStateException("Unexpected value: " + method);
        };

        try {
            var formattedBody = body.formatted(
                userId,
                name,
                fromDate,
                fromTime,
                toDate,
                toTime,
                timezone
            ).replaceAll("%", "");
            return req.content(formattedBody)
                .contentType(contentType);

        } catch (UnknownFormatConversionException ex) {
            return req.content(body.replaceAll("%", "")).contentType(contentType);
        }
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
        return Arbitraries.frequencyOf(
            Tuple.of(10, Arbitraries.of("2025-06-09", "2025-06-10", "2025-06-11", "2025-02-30")),
            Tuple.of(1, Arbitraries.strings())
        );
    }

    @Provide
    Arbitrary<String> times() {
        return Arbitraries.frequencyOf(
            Tuple.of(10, Arbitraries.of("12:40", "01:10", "13:50", "23:30", "99:99")),
            Tuple.of(1, Arbitraries.strings())
        );
    }

    @Provide
    Arbitrary<String> timezones() {
        return Arbitraries.frequencyOf(
            Tuple.of(3, Arbitraries.of("Asia/Kolkata"),
                Tuple.of(3, Arbitraries.of(ZoneId.getAvailableZoneIds())),
                Tuple.of(1, Arbitraries.strings())
            ));
    }

    @Property(tries = 100000)
    @SneakyThrows
    void responsesAreAlwaysValidJson(
        @ForAll("methods") String method,
        @ForAll("paths") String path,
        @ForAll("contentTypes") String contentType,
        @ForAll("bodies") String body,
        @ForAll String name,
        @ForAll("userIds") Long userId,
        @ForAll("dates") String fromDate,
        @ForAll("times") String fromTime,
        @ForAll("dates") String toDate,
        @ForAll("times") String toTime,
        @ForAll("timezones") String timezone) {

        MockHttpServletRequestBuilder req = buildRequest(
            path, contentType, name, userId, fromDate, fromTime, toDate, toTime, timezone, method,
            body);

        mockMvc.perform(req).andDo(res -> {
            assertThat(res.getResponse().getStatus()).isLessThan(500);
            assertThat(res.getResponse().getContentType()).isEqualTo(APPLICATION_JSON_VALUE);
            var responseBody = res.getResponse().getContentAsString();

            assertThat(om.readValue(responseBody, Map.class)).isInstanceOf(Map.class);
        });
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
        return Arbitraries.oneOf(
            Arbitraries.strings().alpha(),
            Arbitraries.of(
                MediaType.ALL_VALUE,
                MediaType.APPLICATION_ATOM_XML_VALUE,
                MediaType.APPLICATION_CBOR_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.APPLICATION_GRAPHQL_RESPONSE_VALUE,
                MediaType.APPLICATION_JSON_VALUE,
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                MediaType.APPLICATION_PDF_VALUE,
                MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                MediaType.APPLICATION_PROBLEM_XML_VALUE,
                MediaType.APPLICATION_PROTOBUF_VALUE,
                MediaType.APPLICATION_RSS_XML_VALUE,
                MediaType.APPLICATION_NDJSON_VALUE,
                MediaType.APPLICATION_XHTML_XML_VALUE,
                MediaType.APPLICATION_XML_VALUE,
                MediaType.APPLICATION_YAML_VALUE,
                MediaType.IMAGE_GIF_VALUE,
                MediaType.IMAGE_JPEG_VALUE,
                MediaType.IMAGE_PNG_VALUE,
                MediaType.MULTIPART_FORM_DATA_VALUE,
                MediaType.MULTIPART_MIXED_VALUE,
                MediaType.MULTIPART_RELATED_VALUE,
                MediaType.TEXT_EVENT_STREAM_VALUE,
                MediaType.TEXT_HTML_VALUE,
                MediaType.TEXT_MARKDOWN_VALUE,
                MediaType.TEXT_PLAIN_VALUE,
                MediaType.TEXT_XML_VALUE)
        );
    }

    @Provide
    Arbitrary<String> paths() {
        return Arbitraries.oneOf(Arbitraries.of(
            "/user",
            "/meeting",
            "/meeting/invite",
            "/meeting/accept",
            "/meeting/reject",
            "/non-existent",
            "/"
        ), Arbitraries.strings().alpha()).map(s -> s.startsWith("/") ? s : "/" + s);
    }

    @Provide
    Arbitrary<String> bodies() {
        return Arbitraries.oneOf(
            Arbitraries.strings(),
            Combinators.combine(Arbitraries.integers(), Arbitraries.integers())
                .as("[%s,%s]"::formatted),
            Arbitraries.of(
                MEETING_CREATION_BODY_JSON,
                MEETING_INVITE_BODY,
                MEETING_INVITE_UPDATE_BODY_JSON,
                "this is random",
                ""
            ));
    }

    @Provide
    Arbitrary<String> methods() {
        return Arbitraries.of(
            "GET", "POST", "PUT", "DELETE"
        );
    }
}
