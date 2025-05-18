package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.RequestUtils;
import me.mourjo.quickmeetings.web.MeetingsController;
import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.Tuple;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.api.DateTimes;
import net.jqwik.time.api.arbitraries.LocalDateTimeArbitrary;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Tag("test-being-demoed")
@JqwikSpringSupport
@WebMvcTest(MeetingsController.class)
@AutoConfigureMockMvc
public class MeetingCreationGenTests {

    private final LocalDateTime MIN_START_TIME = LocalDateTime.of(2025, 1, 15, 0, 0, 0, 0);

    @MockitoBean
    UserService userService;

    @MockitoBean
    MeetingsService meetingsService;

    @Autowired
    MockMvc mockMvc;

    Meeting meeting;
    String meetingName;

    @SneakyThrows
    @Property(tries = 100000, afterFailure = AfterFailureMode.RANDOM_SEED)
    void validMeetingRangeShouldReturn2xx(
        @ForAll("meetingArgs") MeetingArgs meetingArgs) {

        createMeetingAndExpectSuccess(
            meetingArgs.fromDate(),
            meetingArgs.fromTime(),
            meetingArgs.toDate(),
            meetingArgs.toTime(),
            meetingArgs.timezone()
        );
    }

    LocalDateTimeArbitrary startTimeProvider() {
        return DateTimes.dateTimes()
            .atTheEarliest(MIN_START_TIME)
            .atTheLatest(MIN_START_TIME.plusMonths(6));
    }

    @Provide
    Arbitrary<MeetingArgs> meetingArgs() {
        return Combinators.combine(
                startTimeProvider(),
                Arbitraries.of(15, 30), // duration
                zoneIds() // timezone
            ).as(Tuple::of)
            .map(tuple -> {
                var from = tuple.get1();
                var to = from.plusMinutes(tuple.get2());

                var fromDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(from);
                var fromTime = DateTimeFormatter.ofPattern("HH:mm:ss").format(from);

                var toDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(to);
                var toTime = DateTimeFormatter.ofPattern("HH:mm:ss").format(to);

                var timezone = tuple.get3();

                return new MeetingArgs(
                    fromDate, fromTime, toDate, toTime, timezone
                );
            });
    }

    @SneakyThrows
    void createMeetingAndExpectSuccess(String fromDate,
        String fromTime,
        String toDate,
        String toTime,
        String timezone) {
        mockServiceCalls();

        mockMvc.perform(
            createMeetingRequest(
                fromDate,
                fromTime,
                toDate,
                toTime,
                timezone
            )
        ).andExpect(matcher -> assertThat(matcher.getResponse().getContentAsString())
            .containsAnyOf(
                "Meeting created",
                "gap in the local time-line, typically caused by daylight savings"
            )
        ).andReturn();
    }

    @Provide
    Arbitrary<String> zoneIds() {
        var zoneIds = Set.of("Asia/Kolkata", "Europe/Amsterdam");
        return Arbitraries.of(zoneIds);
    }

    @BeforeProperty
    void createUserMeeting() {
        meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());
        meeting = Meeting.builder()
            .name(meetingName)
            .createdAt(OffsetDateTime.now())
            .startAt(OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC))
            .endAt(OffsetDateTime.of(LocalDateTime.now().plusMinutes(10), ZoneOffset.UTC))
            .updatedAt(OffsetDateTime.now())
            .id(918L)
            .build();
    }

    MockHttpServletRequestBuilder createMeetingRequest(
        String fromDate,
        String fromTime,
        String toDate,
        String toTime,
        String timezone) {
        return RequestUtils.meetingCreationRequest(
            1,
            "sample meeting",
            fromDate,
            fromTime,
            toDate,
            toTime,
            timezone
        );
    }

    void mockServiceCalls() {
        Mockito.when(meetingsService.createMeeting(
            any(),
            anyLong(),
            any(ZonedDateTime.class),
            any(ZonedDateTime.class)
        )).thenReturn(meeting);

        Mockito.when(userService.getUser(anyLong())).thenReturn(new User("name", 1));
    }

    record MeetingArgs(String fromDate, String fromTime, String toDate, String toTime,
                       String timezone) {

    }

}