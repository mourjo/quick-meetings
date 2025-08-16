package me.mourjo.quickmeetings.generativetests;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.api.constraints.DateTimeRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Tag("test-being-demoed")
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
    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void overlappingMeetingsCannotBeCreated(

        @ForAll
        @DateTimeRange(min = "2025-02-12T10:00:00", max = "2025-02-12T11:59:59")
        LocalDateTime meeting1Start,

        @ForAll
        @IntRange(min = 1, max = 60)
        int meeting1DurationMins,

        @ForAll
        @DateTimeRange(min = "2025-02-12T10:00:00", max = "2025-02-12T11:59:59")
        LocalDateTime meeting2Start,

        @ForAll
        @IntRange(min = 1, max = 60)
        int meeting2DurationMins

    ) {
        var debbie = userService.createUser("debbie");
        var meeting1End = meeting1Start.plusMinutes(meeting1DurationMins);
        var meeting2End = meeting2Start.plusMinutes(meeting2DurationMins);

        // Create the first meeting
        createMeeting("Debbie's meeting", meeting1Start, debbie, meeting1End);

        // Ask the repository if the second meeting has any overlaps
        var overlappingMeetingsDb = findOverlaps(meeting2Start, debbie, meeting2End);

        // Ask the oracle if the date times overlap - check if the repository result matches
        if (doIntervalsOverlap(meeting1Start, meeting1End, meeting2Start, meeting2End)) {
            assertThat(overlappingMeetingsDb.size()).isEqualTo(1);
        } else {
            assertThat(overlappingMeetingsDb).isEmpty();
        }
    }

    private List<Meeting> findOverlaps(LocalDateTime meeting2Start, User debbie, LocalDateTime meeting2End) {
        return meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            ZonedDateTime.of(meeting2Start, ZoneOffset.UTC).toOffsetDateTime(),
            ZonedDateTime.of(meeting2End, ZoneOffset.UTC).toOffsetDateTime()
        );
    }

    private void createMeeting(String name, LocalDateTime meeting1Start, User debbie, LocalDateTime meeting1End) {
        meetingsService.createMeeting(
            name,
            debbie.id(),
            ZonedDateTime.of(meeting1Start, ZoneOffset.UTC),
            ZonedDateTime.of(meeting1End, ZoneOffset.UTC)
        );
    }
}
