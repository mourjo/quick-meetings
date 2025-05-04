package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.UUID;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.RequestUtils;
import net.jqwik.api.Disabled;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.api.constraints.DateTimeRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Disabled
@JqwikSpringSupport
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
public class OverlappingMeetingsGenTest {

    @Autowired
    MeetingsService meetingsService;

    @Autowired
    MeetingRepository meetingRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserMeetingRepository userMeetingRepository;

    @Autowired
    UserService userService;

    @Autowired
    MockMvc mockMvc;

    private static boolean doIntervalsOverlap(LocalDateTime meeting1Start,
        LocalDateTime meeting1End,
        LocalDateTime meeting2Start, LocalDateTime meeting2End) {
        return meeting1End.isEqual(meeting2Start)
            || meeting1End.isEqual(meeting2End)
            || meeting1Start.isEqual(meeting2Start)
            || meeting1Start.isEqual(meeting2End)

            // meeting 1 starts in between meeting 2's start and end
            || (meeting2Start.isAfter(meeting1Start) && meeting2Start.isBefore(meeting1End))

            // meeting 1 ends in between meeting 2's start and end
            || (meeting1Start.isAfter(meeting2Start) && meeting1Start.isBefore(meeting2End));
    }

    @SneakyThrows
    @Property
    void uniqueInList(
        @ForAll @DateTimeRange(min = "2025-01-01T10:00:00", max = "2025-01-01T20:59:59") LocalDateTime meeting1Start,
        @ForAll @IntRange(min = 1, max = 60) int meeting1DurationMins,
        @ForAll @DateTimeRange(min = "2025-01-01T10:00:00", max = "2025-01-01T20:59:59") LocalDateTime meeting2Start,
        @ForAll @IntRange(min = 1, max = 60) int meeting2DurationMins
    ) {
        var debbie = userService.createUser("debbie");
        var meeting1End = meeting1Start.plusMinutes(meeting1DurationMins);
        var meeting2End = meeting2Start.plusMinutes(meeting2DurationMins);

        // Create the first meeting
        meetingsService.createMeeting(
            "Debbie's meeting",
            debbie.id(),
            ZonedDateTime.of(meeting1Start, ZoneOffset.UTC),
            ZonedDateTime.of(meeting1End, ZoneOffset.UTC)
        );

        // Ask the repository if the second meeting has any overlaps
        var overlappingMeetingsDb = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            ZonedDateTime.of(meeting2Start, ZoneOffset.UTC).toOffsetDateTime(),
            ZonedDateTime.of(meeting2End, ZoneOffset.UTC).toOffsetDateTime()
        );

        // Ask the oracle if the date times overlap - check if the repository result matches
        if (doIntervalsOverlap(meeting1Start, meeting1End, meeting2Start, meeting2End)) {
            assertThat(overlappingMeetingsDb.size()).isEqualTo(1);
        } else {
            assertThat(overlappingMeetingsDb).isEmpty();
        }

    }

    @AfterEach
    @BeforeEach
    void setUp() {
        userMeetingRepository.deleteAll();
        userRepository.deleteAll();
        meetingRepository.deleteAll();
    }

    @SneakyThrows
    Meeting createMeeting(User user, String meetingName, TemporalAccessor from, TemporalAccessor to,
        String zone) {
        var req = RequestUtils.meetingRequest(
            user.id(),
            meetingName,
            from,
            to,
            zone
        );

        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        var meetingId = Long.parseLong(
            JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString()
        );

        return meetingRepository.findById(meetingId).get();
    }

    @SneakyThrows
    public Meeting createMeeting(TemporalAccessor from, TemporalAccessor to, String zone) {
        String meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());
        var user = userService.createUser("random-" + UUID.randomUUID());

        return createMeeting(user, meetingName, from, to, zone);
    }

}
