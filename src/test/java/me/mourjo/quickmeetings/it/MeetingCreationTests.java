package me.mourjo.quickmeetings.it;

import static me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MeetingCreationTests extends BaseIT {

    @Test
    void creationWithRole() {
        var aliceMeetingId = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        for (var meeting : meetingRepository.findAll()) {
            assertThat(meeting.name()).isEqualTo("Alice's meeting");
            assertThat(meeting.startAt().toEpochSecond()).isEqualTo(now.toEpochSecond());
            assertThat(meeting.endAt().toEpochSecond()).isEqualTo(
                now.plusMinutes(60).toEpochSecond());
        }

        for (var um : userMeetingRepository.findAll()) {
            assertThat(um.userId()).isEqualTo(alice.id());
            assertThat(um.meetingId()).isEqualTo(aliceMeetingId);
            assertThat(um.userRole()).isEqualTo(OWNER);
        }
    }

    @Test
    void overlappingOwnerMeetings() {
        meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        meetingsService.createMeeting(
            "Bob's meeting",
            bob.id(),
            now.plusMinutes(10),
            now.plusMinutes(70)
        );

        assertThatExceptionOfType(OverlappingMeetingsException.class).isThrownBy(
            () -> meetingsService.createMeeting(
                "Alice's second meeting",
                bob.id(),
                now.plusMinutes(30),
                now.plusMinutes(90)
            )
        );
    }

    @Test
    void nonexistentOwner() {
        assertThatExceptionOfType(UserNotFoundException.class).isThrownBy(
            () -> meetingsService.createMeeting(
                "Unknown's meeting",
                199L,
                now.plusMinutes(10),
                now.plusMinutes(70)
            )
        ).withMessageContaining("Users [199] not found");
    }

    @Test
    void overlappingAcceptedMeetings() {
        var aliceMeeting = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        meetingsService.invite(aliceMeeting, bob.id());
        meetingUtils.acceptInvite(aliceMeeting, bob.id());

        assertThatExceptionOfType(OverlappingMeetingsException.class).isThrownBy(
            () -> meetingsService.createMeeting(
                "Bob's meeting",
                bob.id(),
                now.plusMinutes(10),
                now.plusMinutes(70)
            )
        );
    }

    @Test
    void overlappingNotAcceptedMeetings() {
        var aliceMeeting = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(60)
        );

        meetingsService.invite(aliceMeeting, bob.id());

        assertThatNoException().isThrownBy(
            () -> meetingsService.createMeeting(
                "Bob's meeting",
                bob.id(),
                now.plusMinutes(10),
                now.plusMinutes(70)
            )
        );
    }
}
