package me.mourjo.quickmeetings.it;

import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.ACCEPTED;
import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.INVITED;
import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Set;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import org.junit.jupiter.api.Test;

public class MeetingInviteAcceptanceTests extends BaseIT {

    @Test
    void acceptSuccessfully() {
        var aliceMeetingId = createAliceMeeting();

        meetingsService.invite(
            aliceMeetingId,
            List.of(bob.id(), charlie.id())
        );

        assertThat(meetingsService.accept(
            aliceMeetingId,
            bob.id()
        )).isTrue();

        assertThatExceptionOfType(OverlappingMeetingsException.class).isThrownBy(
            () -> assertThat(meetingsService.accept(
                aliceMeetingId,
                bob.id()
            )));

        var activeUsers = Set.of(alice.id(), bob.id(), charlie.id());

        userMeetingRepository.findAllByMeetingId(aliceMeetingId)
            .forEach(userMeeting -> {
                    assertThat(userMeeting.meetingId()).isEqualTo(aliceMeetingId);
                    assertThat(userMeeting.userId()).matches(activeUsers::contains);

                    var userId = userMeeting.userId();
                    var userRole = userMeeting.userRole();

                    if (userId == alice.id()) {
                        assertThat(userRole).isEqualTo(OWNER);
                    } else if (userId == bob.id()) {
                        assertThat(userRole).isEqualTo(ACCEPTED);
                    } else {
                        assertThat(userRole).isEqualTo(INVITED);
                    }
                }
            );
    }

    @Test
    void doNotAcceptWhenUninvited() {
        var aliceMeetingId = createAliceMeeting();

        meetingsService.invite(
            aliceMeetingId,
            List.of(bob.id(), charlie.id())
        );

        assertThat(meetingsService.accept(
            aliceMeetingId,
            erin.id()
        )).isFalse();

        assertThatExceptionOfType(UserNotFoundException.class).isThrownBy(
            () -> meetingsService.accept(
                aliceMeetingId,
                9991L // unknown meeting
            )
        );
    }

    @Test
    void doNotAcceptUnknownMeeting() {
        assertThatExceptionOfType(MeetingNotFoundException.class).isThrownBy(
            () -> meetingsService.accept(
                9929L, // unknown meeting
                frank.id()
            ));
    }

    long createAliceMeeting() {
        return meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(30)
        ).id();
    }
}
