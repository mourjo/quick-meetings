package me.mourjo.quickmeetings.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.RequestUtils;
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
public class GenerativeMeetingsIntegrationTest {

    @MockitoBean
    UserService userService;

    @MockitoBean
    MeetingsService meetingsService;

    @Autowired
    MockMvc mockMvc;

    @SneakyThrows
    @Property(tries = 100000)
    void uniqueInList(
        @ForAll @DateTimeRange(min = "2025-01-01T00:00:00", max = "2025-12-31T00:00:00") LocalDateTime localDateTime,
        @ForAll("zoneIds") ZoneId zone) {
        var from = localDateTime;
        var to = from.plusMinutes(30);
        String tz = zone.getDisplayName(TextStyle.NARROW, Locale.US);

        String meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());

        var req = RequestUtils.meetingRequest(
            1,
            meetingName,
            from,
            to,
            tz
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

        assertThat(
            Duration.between(fromCap.getValue(), toCap.getValue())
        ).isEqualTo(Duration.ofMinutes(30));

    }

    @Provide
    Arbitrary<ZoneId> zoneIds() {
        Set<String> zoneIds = ZoneId.getAvailableZoneIds();
        return Arbitraries.of(zoneIds)
            .map(ZoneId::of);
    }

}