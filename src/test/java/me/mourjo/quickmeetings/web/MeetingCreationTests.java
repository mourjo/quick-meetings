package me.mourjo.quickmeetings.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MeetingCreationTests {

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

    @SneakyThrows
    @Test
    void createMeetingTest() {
        var startTime = LocalDateTime.of(2025, 3, 30, 14, 30, 0, 0);
        var zone = "Europe/Amsterdam";
        var meetingDuration = Duration.ofMinutes(30);

        var dbMeeting = createMeeting(
            startTime,
            startTime.plus(meetingDuration),
            zone
        );

        var dbMeetingDuration = Duration.between(dbMeeting.startAt(), dbMeeting.endAt());
        assertThat(dbMeetingDuration).isEqualTo(meetingDuration);
    }

    @SneakyThrows
    @Test
    void userMeetingTest() {
        var startTime = LocalDateTime.of(2025, 3, 30, 14, 30, 0, 0);
        var zone = "Europe/Amsterdam";
        var meetingDuration = Duration.ofMinutes(30);
        var debbie = userService.createUser("debbie");
        var justin = userService.createUser("justin");
        var peter = userService.createUser("peter");

        createMeeting(
            justin,
            "Justin's meeting",
            startTime,
            startTime.plus(meetingDuration),
            zone
        );

        createMeeting(
            debbie,
            "Debbie's meeting",
            startTime,
            startTime.plus(meetingDuration),
            zone
        );

        var debbieMeetings = meetingRepository.findAllMeetingsForUser(debbie.id());
        assertThat(debbieMeetings.size()).isEqualTo(1);
        assertThat(debbieMeetings.get(0).name()).isEqualTo("Debbie's meeting");

        var justinMeetings = meetingRepository.findAllMeetingsForUser(justin.id());
        assertThat(justinMeetings.size()).isEqualTo(1);
        assertThat(justinMeetings.get(0).name()).isEqualTo("Justin's meeting");

        var peterMeetings = meetingRepository.findAllMeetingsForUser(peter.id());
        assertThat(peterMeetings).isEmpty();
    }

    @AfterEach
    @BeforeEach
    void setUp() {
        userMeetingRepository.deleteAll();
        userRepository.deleteAll();
        meetingRepository.deleteAll();
    }

    @Test
    void overlappingMeetingTest() {
        var startTime = LocalDateTime.of(2025, 3, 30, 14, 30, 0, 0);
        var zone = "Asia/Kolkata";
        var meetingDuration = Duration.ofMinutes(30);
        var debbie = userService.createUser("debbie");
        createMeeting(
            debbie,
            "Debbie's meeting",
            startTime,
            startTime.plus(meetingDuration),
            zone
        );

        var overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            startTime.atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
            startTime.plus(meetingDuration).atOffset(ZoneOffset.ofHoursMinutes(5, 30))
        );

        assertThat(overlappingMeetings).hasSize(1);

        var fifteenMinsLater = startTime.plusMinutes(15);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            fifteenMinsLater.atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
            fifteenMinsLater.plus(meetingDuration).atOffset(ZoneOffset.ofHoursMinutes(5, 30))
        );
        assertThat(overlappingMeetings).hasSize(1);

        var fifteenMinsEarlier = startTime.minusMinutes(15);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            fifteenMinsEarlier.atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
            fifteenMinsEarlier.plus(meetingDuration).atOffset(ZoneOffset.ofHoursMinutes(5, 30))
        );
        assertThat(overlappingMeetings).hasSize(1);

        var muchLater = startTime.plusHours(10);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            muchLater.atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
            muchLater.plus(meetingDuration).atOffset(ZoneOffset.ofHoursMinutes(5, 30))
        );
        assertThat(overlappingMeetings).isEmpty();

        var muchEarlier = startTime.minusHours(10);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            muchEarlier.atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
            muchEarlier.plus(meetingDuration).atOffset(ZoneOffset.ofHoursMinutes(5, 30))
        );
        assertThat(overlappingMeetings).isEmpty();

        var later = startTime.plusMinutes(5);

        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            later.atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
            later.plus(Duration.ofMinutes(5)).atOffset(ZoneOffset.ofHoursMinutes(5, 30))
        );
        assertThat(overlappingMeetings).hasSize(1);

        var earlier = startTime.minusDays(10);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            earlier.atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
            earlier.plus(Duration.ofDays(200)).atOffset(ZoneOffset.ofHoursMinutes(5, 30))
        );
        assertThat(overlappingMeetings).hasSize(1);
    }


    @SneakyThrows
    Meeting createMeeting(User user, String meetingName, LocalDateTime from, LocalDateTime to,
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
    Meeting createMeeting(LocalDateTime from, LocalDateTime to, String zone) {
        String meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());
        var user = userService.createUser("random-" + UUID.randomUUID());

        return createMeeting(user, meetingName, from, to, zone);
    }
}