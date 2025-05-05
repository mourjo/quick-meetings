package me.mourjo.quickmeetings.web;

import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.ACCEPTED;
import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.INVITED;
import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.OWNER;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeeting;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
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

    User alice;
    User bob;
    User charlie;
    User dick;

    ZonedDateTime now = ZonedDateTime.now();

    @AfterEach
    void teardown() {
        userMeetingRepository.deleteAll();
        userRepository.deleteAll();
        meetingRepository.deleteAll();
    }


    @BeforeEach
    void setup() {
        alice = userService.createUser("Alice");
        bob = userService.createUser("Bob");
        charlie = userService.createUser("Charlie");
        dick = userService.createUser("Dick");
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
        var aliceMeetingId = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        meetingsService.invite(aliceMeetingId, List.of(bob.id(), charlie.id()));

        var bobMeetingId = meetingsService.createMeeting(
            "Bob's meeting",
            bob.id(),
            now.plusMinutes(10),
            now.plusMinutes(70)
        );

        var invitedSuccessfully = meetingsService.invite(bobMeetingId, List.of(charlie.id()));
        assertThat(invitedSuccessfully).isTrue();

        acceptInvite(bobMeetingId, charlie.id());

        var dickMeetingId = meetingsService.createMeeting(
            "Dick's meeting",
            dick.id(),
            now.plusMinutes(20),
            now.plusMinutes(80)
        );

        invitedSuccessfully = meetingsService.invite(dickMeetingId, List.of(charlie.id()));
        assertThat(invitedSuccessfully).isFalse();
    }

    void acceptInvite(long meetingId, long userId) {
        userMeetingRepository.findAllByMeetingId(meetingId).stream()
            .filter(userMeeting -> userMeeting.userId() == userId)
            .forEach(invite -> userMeetingRepository.save(
                UserMeeting.buildFrom(invite).userRole(ACCEPTED).build()
            ));
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
        validateMeetingRole(aliceMeetingId, alice.id(), OWNER);
        validateMeetingRole(aliceMeetingId, bob.id(), INVITED);
        validateMeetingRole(aliceMeetingId, charlie.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeetingId).size()).isEqualTo(3);

        meetingsService.invite(aliceMeetingId, List.of(charlie.id()));
        validateMeetingRole(aliceMeetingId, alice.id(), OWNER);
        validateMeetingRole(aliceMeetingId, bob.id(), INVITED);
        validateMeetingRole(aliceMeetingId, charlie.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeetingId).size()).isEqualTo(3);

        meetingsService.invite(aliceMeetingId, List.of(dick.id()));
        validateMeetingRole(aliceMeetingId, alice.id(), OWNER);
        validateMeetingRole(aliceMeetingId, bob.id(), INVITED);
        validateMeetingRole(aliceMeetingId, charlie.id(), INVITED);
        validateMeetingRole(aliceMeetingId, dick.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeetingId).size()).isEqualTo(4);
    }


    void validateMeetingRole(long meetingId, long userId, RoleOfUser role) {
        var userMeetings = userMeetingRepository.findAllByMeetingId(meetingId);
        var meetingRoles = userMeetings.stream()
            .filter(um -> um.userId() == userId)
            .toList();
        assertThat(meetingRoles.size()).isEqualTo(1);
        assertThat(meetingRoles.get(0).userRole()).isEqualTo(role);
    }
}
