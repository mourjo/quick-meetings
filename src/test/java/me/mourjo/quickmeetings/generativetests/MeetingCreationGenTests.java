package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.RequestUtils;
import me.mourjo.quickmeetings.web.MeetingsController;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Disabled;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.api.constraints.DateTimeRange;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Disabled
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
    @Property(tries = 100000)
    void uniqueInList(
        @ForAll @DateTimeRange(min = "2025-01-01T00:00:00", max = "2025-12-31T23:59:59") LocalDateTime startTime,
        @ForAll("zoneIds") String timezone) {
        var meetingDuration = Duration.ofMinutes(30);

        var dateRange = createMeeting(
            startTime,
            startTime.plus(meetingDuration),
            timezone
        );

        var dbFrom = dateRange.get(0).getValue();
        var dbTo = dateRange.get(1).getValue();
        var dbDuration = Duration.between(dbFrom, dbTo);

        assertThat(dbDuration).isEqualTo(meetingDuration);
    }

    @SneakyThrows
    List<ArgumentCaptor<ZonedDateTime>> createMeeting(LocalDateTime from, LocalDateTime to,
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

        Mockito.when(meetingsService.createMeeting(
            any(),
            anyLong(),
            fromCap.capture(),
            toCap.capture()
        )).thenReturn(100L);

        Mockito.when(userService.getUser(anyLong())).thenReturn(new User("name", 1));

        mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();

        return List.of(fromCap, toCap);
    }

    @Provide
    Arbitrary<String> zoneIds() {
        var zoneIds = Set.of("Asia/Kolkata", "Europe/Amsterdam"); // ZoneId.getAvailableZoneIds()
        return Arbitraries.of(zoneIds);
    }

}