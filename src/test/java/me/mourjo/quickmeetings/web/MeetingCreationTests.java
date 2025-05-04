package me.mourjo.quickmeetings.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.UUID;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
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

        var justinMeeting = createMeeting(
            justin,
            "Justin's meeting",
            startTime,
            startTime.plus(meetingDuration),
            zone
        );

        var debbieMeeting = createMeeting(
            debbie,
            "Debbie's meeting",
            startTime,
            startTime.plus(meetingDuration),
            zone
        );

        var debbieMeetings = meetingRepository.findAllMeetingsForUser(debbie.id());
        assertThat(debbieMeetings.size()).isEqualTo(1);
        verifyMeetingName(debbieMeeting.id(), "Debbie's meeting");
        verifyRole(debbieMeeting.id(), debbie.id(), RoleOfUser.OWNER);

        var justinMeetings = meetingRepository.findAllMeetingsForUser(justin.id());
        assertThat(justinMeetings.size()).isEqualTo(1);
        verifyMeetingName(justinMeeting.id(), "Justin's meeting");

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
        var startTime = OffsetDateTime.of(
            LocalDateTime.of(2025, 3, 30, 14, 30, 0, 0),
            ZoneOffset.ofHoursMinutes(5, 30)
        );
        var zone = "Asia/Kolkata";
        var thirtyMins = Duration.ofMinutes(30);
        var debbie = userService.createUser("debbie");

        createMeeting(
            debbie,
            "Debbie's meeting",
            startTime,
            startTime.plus(thirtyMins),
            zone
        );

        // -----------------------------------------------------------------------------------
        var overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            startTime,
            startTime.plus(thirtyMins)
        );
        assertThat(overlappingMeetings).hasSize(1);

        // -----------------------------------------------------------------------------------

        var fifteenMinsLater = startTime.plusMinutes(15);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            fifteenMinsLater,
            fifteenMinsLater.plus(thirtyMins)
        );
        assertThat(overlappingMeetings).hasSize(1);

        // -----------------------------------------------------------------------------------

        var fifteenMinsEarlier = startTime.minusMinutes(15);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            fifteenMinsEarlier,
            fifteenMinsEarlier.plus(thirtyMins)
        );
        assertThat(overlappingMeetings).hasSize(1);

        // -----------------------------------------------------------------------------------

        var tenHoursLater = startTime.plusHours(10);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            tenHoursLater,
            tenHoursLater.plus(thirtyMins)
        );
        assertThat(overlappingMeetings).isEmpty();

        // -----------------------------------------------------------------------------------

        var tenHoursEarlier = startTime.minusHours(10);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            tenHoursEarlier,
            tenHoursEarlier.plus(thirtyMins)
        );
        assertThat(overlappingMeetings).isEmpty();

        // -----------------------------------------------------------------------------------

        var fiveMinsLater = startTime.plusMinutes(5);
        overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            debbie.id(),
            fiveMinsLater,
            fiveMinsLater.plus(Duration.ofMinutes(5))
        );
        assertThat(overlappingMeetings).hasSize(1);
    }

    private void verifyRole(long meetingId, long userId, RoleOfUser role) {
        var meetings = userMeetingRepository.findAllByMeetingId(meetingId);
        assertThat(meetings.size()).isEqualTo(1);
        assertThat(meetings.get(0).userRole()).isEqualTo(RoleOfUser.OWNER);
        assertThat(meetings.get(0).userId()).isEqualTo(userId);
    }

    private void verifyMeetingName(long meetingId, String name) {
        var maybeMeeting = meetingRepository.findById(meetingId);
        assertThat(maybeMeeting).isPresent();

        var meeting = maybeMeeting.get();
        assertThat(meeting.name()).isEqualTo(name);
    }

    @SneakyThrows
    private Meeting createMeeting(User user, String meetingName, TemporalAccessor from,
        TemporalAccessor to,
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
    private Meeting createMeeting(TemporalAccessor from, TemporalAccessor to, String zone) {
        String meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());
        var user = userService.createUser("random-" + UUID.randomUUID());

        return createMeeting(user, meetingName, from, to, zone);
    }
}