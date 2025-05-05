package me.mourjo.quickmeetings.it;

import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.INVITED;
import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.OWNER;
import static me.mourjo.quickmeetings.utils.UserUtils.alice;
import static me.mourjo.quickmeetings.utils.UserUtils.bob;
import static me.mourjo.quickmeetings.utils.UserUtils.charlie;
import static me.mourjo.quickmeetings.utils.UserUtils.dick;
import static me.mourjo.quickmeetings.utils.UserUtils.erin;
import static me.mourjo.quickmeetings.utils.UserUtils.frank;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.MeetingUtils;
import me.mourjo.quickmeetings.utils.UserUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MeetingInviteTests {

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

    MeetingUtils meetingUtils;
    UserUtils userUtils;

    ZonedDateTime now = ZonedDateTime.now();

    @AfterEach
    void teardown() {
        userMeetingRepository.deleteAll();
        userRepository.deleteAll();
        meetingRepository.deleteAll();
    }


    @BeforeEach
    void setup() {
        meetingUtils = new MeetingUtils(
            userService, meetingsService, meetingRepository, userRepository, userMeetingRepository
        );
        userUtils = new UserUtils(
            userService, userRepository
        );
    }

    @Test
    void inviteUsersToNonExistentMeeting() {
        assertThatExceptionOfType(MeetingNotFoundException.class)
            .isThrownBy(
                () -> meetingsService.invite(11, List.of(bob.id(), charlie.id()))
            ).withMessageContaining("Meeting 11 not found");

        assertThatExceptionOfType(MeetingNotFoundException.class)
            .isThrownBy(
                () -> meetingsService.invite(12, List.of(9999L))
            ).withMessageContaining("Meeting 12 not found");
    }

    @Test
    void inviteNonExistentUsersToMeeting() {
        var aliceMeetingId = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        assertThatExceptionOfType(UserNotFoundException.class)
            .isThrownBy(
                () -> meetingsService.invite(aliceMeetingId, List.of(98L))
            ).withMessageContaining("Users [98] not found");

        assertThatExceptionOfType(UserNotFoundException.class)
            .isThrownBy(
                () -> meetingsService.invite(aliceMeetingId, List.of(bob.id(), charlie.id(), 99L))
            ).withMessageContaining("Users [99] not found");
    }

    @Test
    void overlappingMeetingInvites() {
        var aliceMeetingStart = now;
        var aliceMeetingEnd = now.plusMinutes(60);
        var aliceMeetingId = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            aliceMeetingStart,
            aliceMeetingEnd
        );

        meetingsService.invite(aliceMeetingId, List.of(bob.id(), charlie.id()));

        var bobMeetingId = meetingsService.createMeeting(
            "Bob's meeting",
            bob.id(),
            aliceMeetingStart.plusMinutes(10),
            aliceMeetingEnd.plusMinutes(10)
        );

        var invitedSuccessfully = meetingsService.invite(bobMeetingId, List.of(charlie.id()));
        assertThat(invitedSuccessfully).isTrue();

        meetingUtils.acceptInvite(bobMeetingId, charlie.id());
        var dickMeetingId = meetingsService.createMeeting(
            "Dick's meeting",
            dick.id(),
            aliceMeetingStart.plusMinutes(20),
            aliceMeetingEnd.plusMinutes(20)
        );
        invitedSuccessfully = meetingsService.invite(dickMeetingId, List.of(charlie.id()));
        assertThat(invitedSuccessfully).isFalse();

        meetingsService.createMeeting(
            "Erin's meeting",
            erin.id(),
            aliceMeetingStart,
            aliceMeetingEnd.minusMinutes(30)
        );
        invitedSuccessfully = meetingsService.invite(bobMeetingId, List.of(frank.id()));
        assertThat(invitedSuccessfully).isTrue();
        invitedSuccessfully = meetingsService.invite(bobMeetingId, List.of(alice.id()));
        assertThat(invitedSuccessfully).isFalse();
    }


    @Test
    void inviteUsersToMeeting() {
        var aliceMeetingId = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        assertThat(userMeetingRepository.count()).isEqualTo(1);

        var invitedSuccessfully = meetingsService.invite(
            aliceMeetingId,
            List.of(bob.id(), charlie.id())
        );
        assertThat(invitedSuccessfully).isTrue();

        meetingsService.invite(aliceMeetingId, List.of(bob.id(), charlie.id()));
        meetingUtils.validateMeetingRole(aliceMeetingId, alice.id(), OWNER);
        meetingUtils.validateMeetingRole(aliceMeetingId, bob.id(), INVITED);
        meetingUtils.validateMeetingRole(aliceMeetingId, charlie.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeetingId).size()).isEqualTo(3);

        meetingsService.invite(aliceMeetingId, List.of(charlie.id()));
        meetingUtils.validateMeetingRole(aliceMeetingId, alice.id(), OWNER);
        meetingUtils.validateMeetingRole(aliceMeetingId, bob.id(), INVITED);
        meetingUtils.validateMeetingRole(aliceMeetingId, charlie.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeetingId).size()).isEqualTo(3);

        meetingsService.invite(aliceMeetingId, List.of(dick.id()));
        meetingUtils.validateMeetingRole(aliceMeetingId, alice.id(), OWNER);
        meetingUtils.validateMeetingRole(aliceMeetingId, bob.id(), INVITED);
        meetingUtils.validateMeetingRole(aliceMeetingId, charlie.id(), INVITED);
        meetingUtils.validateMeetingRole(aliceMeetingId, dick.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeetingId).size()).isEqualTo(4);
    }
}
