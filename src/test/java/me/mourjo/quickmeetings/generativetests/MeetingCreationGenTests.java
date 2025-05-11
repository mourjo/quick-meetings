package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.api.constraints.DateTimeRange;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

    @SneakyThrows
    @Property(tries = 100000, afterFailure = AfterFailureMode.RANDOM_SEED)
    void validMeetingRangeShouldReturn2xx(
        @ForAll @DateTimeRange(min = "2025-01-01T00:00:00", max = "2025-06-01T00:00:00") LocalDateTime startTime,
        @ForAll @IntRange(min = 1, max = 30) int durationMins,
        @ForAll("zoneIds") String timezone) {

        createMeetingAndExpectSuccess(
            startTime,
            startTime.plusMinutes(durationMins),
            timezone
        );

    }

    @SneakyThrows
    void createMeetingAndExpectSuccess(LocalDateTime from, LocalDateTime to,
        String zone) {
        String meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());

        var req = RequestUtils.meetingCreationRequest(
            1,
            meetingName,
            from,
            to,
            zone
        );

        var fromCap = ArgumentCaptor.forClass(ZonedDateTime.class);
        var toCap = ArgumentCaptor.forClass(ZonedDateTime.class);

        var meeting = Meeting.builder()
            .name(meetingName)
            .createdAt(OffsetDateTime.now())
            .startAt(OffsetDateTime.of(from, ZoneOffset.UTC))
            .endAt(OffsetDateTime.of(to, ZoneOffset.UTC))
            .updatedAt(OffsetDateTime.now())
            .id(918L)
            .build();

        Mockito.when(meetingsService.createMeeting(
            any(),
            anyLong(),
            fromCap.capture(),
            toCap.capture()
        )).thenReturn(meeting);

        Mockito.when(userService.getUser(anyLong())).thenReturn(new User("name", 1));
        mockMvc.perform(req)
            .andExpect(
                matcher -> assertThat(matcher.getResponse().getContentAsString()).contains(
                    "Meeting created"))
            .andExpect(status().is2xxSuccessful()).andReturn();

    }

    @Provide
    Arbitrary<String> zoneIds() {
        var zoneIds = Set.of("Asia/Kolkata", "Europe/Amsterdam"); // ZoneId.getAvailableZoneIds()
        return Arbitraries.of(zoneIds);
    }

}