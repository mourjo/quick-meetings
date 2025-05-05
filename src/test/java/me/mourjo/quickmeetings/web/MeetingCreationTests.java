package me.mourjo.quickmeetings.web;

import static me.mourjo.quickmeetings.utils.RequestUtils.readJsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.UUID;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
import me.mourjo.quickmeetings.it.BaseIT;
import me.mourjo.quickmeetings.utils.RequestUtils;
import org.junit.jupiter.api.Test;

class MeetingCreationTests extends BaseIT {

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

        var debbieMeetings = meetingRepository.findAllConfirmedMeetingsForUser(debbie.id());
        assertThat(debbieMeetings.size()).isEqualTo(1);
        meetingUtils.validateMeetingName(debbieMeeting.id(), "Debbie's meeting");
        meetingUtils.validateMeetingRole(debbieMeeting.id(), debbie.id(), RoleOfUser.OWNER);

        var justinMeetings = meetingRepository.findAllConfirmedMeetingsForUser(justin.id());
        assertThat(justinMeetings.size()).isEqualTo(1);
        meetingUtils.validateMeetingName(justinMeeting.id(), "Justin's meeting");

        var peterMeetings = meetingRepository.findAllConfirmedMeetingsForUser(peter.id());
        assertThat(peterMeetings).isEmpty();
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


    @SneakyThrows
    private Meeting createMeeting(User user, String meetingName, TemporalAccessor from,
        TemporalAccessor to,
        String zone) {
        var req = RequestUtils.meetingCreationRequest(
            user.id(),
            meetingName,
            from,
            to,
            zone
        );

        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        var meetingId = Long.parseLong(readJsonPath(result, "$.id"));
        return meetingRepository.findById(meetingId).get();
    }

    @SneakyThrows
    private Meeting createMeeting(TemporalAccessor from, TemporalAccessor to, String zone) {
        String meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());
        var user = userService.createUser("random-" + UUID.randomUUID());

        return createMeeting(user, meetingName, from, to, zone);
    }
}