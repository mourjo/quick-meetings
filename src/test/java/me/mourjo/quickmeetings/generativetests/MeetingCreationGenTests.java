package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.api.constraints.DateTimeRange;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@JqwikSpringSupport
@WebMvcTest(MeetingsController.class)
@AutoConfigureMockMvc
public class MeetingCreationGenTests {

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
        @ForAll @DateTimeRange(min = "2025-01-01T00:00:00", max = "2025-06-01T00:00:00") LocalDateTime startTime,
        @ForAll @IntRange(min = 15, max = 30) int durationMins,
        @ForAll("zoneIds") String timezone) {

        createMeetingAndExpectSuccess(
            startTime,
            startTime.plusMinutes(durationMins),
            timezone
        );
    }

    @SneakyThrows
    void createMeetingAndExpectSuccess(LocalDateTime from, LocalDateTime to, String zone) {
        mockServiceCalls();

        mockMvc.perform(createMeetingRequest(from, to, zone))
            .andExpect(
                matcher -> assertThat(matcher.getResponse().getContentAsString()).containsAnyOf(
                    "does not exist in zone",
                    "Meeting created"))
            .andReturn();
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

    MockHttpServletRequestBuilder createMeetingRequest(LocalDateTime from, LocalDateTime to,
        String zone) {
        return RequestUtils.meetingCreationRequest(
            1,
            meetingName,
            from,
            to,
            zone
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

}