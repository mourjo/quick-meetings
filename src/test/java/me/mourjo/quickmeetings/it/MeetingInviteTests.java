package me.mourjo.quickmeetings.it;

import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.ACCEPTED;
import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.INVITED;
import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.OWNER;
import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.REJECTED;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import org.junit.jupiter.api.Test;

public class MeetingInviteTests extends BaseIT {

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
        var aliceMeeting = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        assertThatExceptionOfType(UserNotFoundException.class)
            .isThrownBy(
                () -> meetingsService.invite(aliceMeeting.id(), List.of(98L))
            ).withMessageContaining("Users [98] not found");

        assertThatExceptionOfType(UserNotFoundException.class)
            .isThrownBy(
                () -> meetingsService.invite(aliceMeeting.id(),
                    List.of(bob.id(), charlie.id(), 99L))
            ).withMessageContaining("Users [99] not found");
    }

    @Test
    void overlappingMeetingInvites() {
        var aliceMeetingStart = now;
        var aliceMeetingEnd = now.plusMinutes(60);
        var aliceMeeting = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            aliceMeetingStart,
            aliceMeetingEnd
        );

        meetingsService.invite(aliceMeeting.id(), List.of(bob.id(), charlie.id()));

        var bobMeeting = meetingsService.createMeeting(
            "Bob's meeting",
            bob.id(),
            aliceMeetingStart.plusMinutes(10),
            aliceMeetingEnd.plusMinutes(10)
        );

        var invitations = meetingsService.invite(bobMeeting.id(), List.of(charlie.id()));
        assertThat(invitations.size()).isGreaterThan(0);

        meetingsService.accept(bobMeeting.id(), charlie.id());
        var dickMeeting = meetingsService.createMeeting(
            "Dick's meeting",
            dick.id(),
            aliceMeetingStart.plusMinutes(20),
            aliceMeetingEnd.plusMinutes(20)
        );
        assertThatExceptionOfType(OverlappingMeetingsException.class)
            .isThrownBy(() -> meetingsService.invite(dickMeeting.id(), List.of(charlie.id())));

        meetingsService.createMeeting(
            "Erin's meeting",
            erin.id(),
            aliceMeetingStart,
            aliceMeetingEnd.minusMinutes(30)
        );
        invitations = meetingsService.invite(bobMeeting.id(), List.of(frank.id()));
        assertThat(invitations.size()).isGreaterThan(0);

        assertThatExceptionOfType(OverlappingMeetingsException.class)
            .isThrownBy(() -> meetingsService.invite(bobMeeting.id(), List.of(alice.id())));

    }


    @Test
    void inviteUsersToMeeting() {
        var aliceMeeting = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        assertThat(userMeetingRepository.count()).isEqualTo(1);

        var invitations = meetingsService.invite(
            aliceMeeting.id(),
            List.of(bob.id(), charlie.id())
        );
        assertThat(invitations.size()).isGreaterThan(0);

        meetingsService.invite(aliceMeeting.id(), List.of(bob.id(), charlie.id()));
        meetingUtils.validateMeetingRole(aliceMeeting.id(), alice.id(), OWNER);
        meetingUtils.validateMeetingRole(aliceMeeting.id(), bob.id(), INVITED);
        meetingUtils.validateMeetingRole(aliceMeeting.id(), charlie.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeeting.id()).size()).isEqualTo(3);

        meetingsService.invite(aliceMeeting.id(), List.of(charlie.id()));
        meetingUtils.validateMeetingRole(aliceMeeting.id(), alice.id(), OWNER);
        meetingUtils.validateMeetingRole(aliceMeeting.id(), bob.id(), INVITED);
        meetingUtils.validateMeetingRole(aliceMeeting.id(), charlie.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeeting.id()).size()).isEqualTo(3);

        meetingsService.invite(aliceMeeting.id(), List.of(dick.id()));
        meetingUtils.validateMeetingRole(aliceMeeting.id(), alice.id(), OWNER);
        meetingUtils.validateMeetingRole(aliceMeeting.id(), bob.id(), INVITED);
        meetingUtils.validateMeetingRole(aliceMeeting.id(), charlie.id(), INVITED);
        meetingUtils.validateMeetingRole(aliceMeeting.id(), dick.id(), INVITED);
        assertThat(userMeetingRepository.findAllByMeetingId(aliceMeeting.id()).size()).isEqualTo(4);
    }

    @Test
    void rejectInvites() {
        var frankMeeting = meetingsService.createMeeting(
            "Frank's meeting",
            frank.id(),
            now,
            now.plusMinutes(30)
        );

        assertThat(userMeetingRepository.count()).isEqualTo(1);

        meetingsService.invite(
            frankMeeting.id(),
            List.of(bob.id(), charlie.id())
        );

        assertThat(meetingsService.reject(frankMeeting.id(), bob.id())).isTrue();
        meetingUtils.validateMeetingRole(frankMeeting.id(), frank.id(), OWNER);
        meetingUtils.validateMeetingRole(frankMeeting.id(), bob.id(), REJECTED);
        meetingUtils.validateMeetingRole(frankMeeting.id(), charlie.id(), INVITED);

        meetingsService.accept(frankMeeting.id(), charlie.id());
        meetingUtils.validateMeetingRole(frankMeeting.id(), charlie.id(), ACCEPTED);

        assertThat(meetingsService.reject(frankMeeting.id(), charlie.id())).isTrue();
    }

    @Test
    void inviteSelfToMeeting() {
        var aliceMeeting = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );
        assertThatExceptionOfType(OverlappingMeetingsException.class)
            .isThrownBy(() -> meetingsService.invite(aliceMeeting.id(), List.of(alice.id())));
        meetingUtils.validateMeetingRole(aliceMeeting.id(), alice.id(), OWNER);
    }
}
