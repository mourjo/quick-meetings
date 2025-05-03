package me.mourjo.quickmeetings.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.RequestUtils;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.api.constraints.DateTimeRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@JqwikSpringSupport
@Transactional
@SpringBootTest
@AutoConfigureMockMvc
public class GenerativeMeetingsIntegrationTest {

    @Autowired
    MeetingRepository meetingRepository;

    @Autowired
    UserMeetingRepository userMeetingRepository;

    @Autowired
    UserService userService;

    @Autowired
    MockMvc mockMvc;

    @SneakyThrows
    @Property(tries = 10000)
    void uniqueInList(
        @ForAll @DateTimeRange(min = "2025-03-20T00:00:00", max = "2025-04-01T00:00:00") LocalDateTime localDateTime,
        @ForAll("zoneIds") ZoneId zone) {
        var from = localDateTime;
        var to = from.plusMinutes(30);
        String tz = zone.getDisplayName(TextStyle.NARROW, Locale.US);

        assertThat(OffsetDateTime.of(from, zone.getRules().getOffset(Instant.now()))).isNotNull();

        var user = userService.createUser("justin");
        String meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());

        var req = RequestUtils.meetingRequest(
            user.id(),
            meetingName,
            from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            from.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            to.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            tz
        );

        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();

        var meetingId = Long.parseLong(
            JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString());

        var dbMeeting = meetingRepository.findById(meetingId).get();
        var userMeetings = userMeetingRepository.findAllByMeetingId(dbMeeting.id());

        assertThat(userMeetings.size()).isEqualTo(1);
        assertThat(userMeetings.get(0).userId()).isEqualTo(user.id());

        assertThat(dbMeeting.startAt()).isEqualTo(
            OffsetDateTime.of(from, zone.getRules().getOffset(from)));

        assertThat(
            Duration.between(dbMeeting.startAt(), dbMeeting.endAt())
        ).isEqualTo(Duration.ofMinutes(30));

    }

    @Provide
    Arbitrary<ZoneId> zoneIds() {
        Set<String> zoneIds = ZoneId.getAvailableZoneIds();
        return Arbitraries.of(Set.of("Asia/Kolkata"))
            .map(ZoneId::of);
    }

}