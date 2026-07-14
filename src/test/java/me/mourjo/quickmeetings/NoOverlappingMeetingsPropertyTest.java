package me.mourjo.quickmeetings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Property-based test that verifies the invariant: a person must never be part of two
 * meetings that overlap in time. The test generates random sequences of meeting operations
 * (create users, create meetings, invite users, accept/reject invites) and asserts the
 * invariant holds after every sequence.
 */
@SpringBootTest
@JqwikSpringSupport
class NoOverlappingMeetingsPropertyTest {

    @Autowired
    MeetingsService meetingsService;

    @Autowired
    UserService userService;

    @Autowired
    MeetingRepository meetingRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * Clean all data after each try so that each generated test case starts from a fresh state.
     */
    @AfterTry
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM user_meetings");
        jdbcTemplate.execute("DELETE FROM meetings");
        jdbcTemplate.execute("DELETE FROM users");
    }

    /**
     * A sealed interface representing the actions that can be performed on the system.
     */
    sealed interface MeetingAction {

        /**
         * Create a meeting for a user at a specific time window.
         *
         * @param userIndex     index into the list of created users (modular)
         * @param name          meeting name
         * @param startMinutes  start time as minutes offset from a base time
         * @param durationMinutes duration of the meeting in minutes
         */
        record CreateMeeting(int userIndex, String name, int startMinutes,
                             int durationMinutes) implements MeetingAction {

        }

        /**
         * Invite a user to an existing meeting.
         *
         * @param userIndex    index of the user to invite
         * @param meetingIndex index of the meeting to invite to
         */
        record InviteToMeeting(int userIndex, int meetingIndex) implements MeetingAction {

        }

        /**
         * Accept a meeting invitation.
         *
         * @param userIndex    index of the user accepting
         * @param meetingIndex index of the meeting to accept
         */
        record AcceptInvite(int userIndex, int meetingIndex) implements MeetingAction {

        }

        /**
         * Reject a meeting invitation.
         *
         * @param userIndex    index of the user rejecting
         * @param meetingIndex index of the meeting to reject
         */
        record RejectInvite(int userIndex, int meetingIndex) implements MeetingAction {

        }
    }

    @Provide
    Arbitrary<List<MeetingAction>> meetingActions() {
        Arbitrary<MeetingAction> createMeeting = Combinators.combine(
            Arbitraries.integers().between(0, 4),       // userIndex
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10), // name
            Arbitraries.integers().between(0, 1440),    // startMinutes (within a day)
            Arbitraries.integers().between(15, 120)     // durationMinutes
        ).as(MeetingAction.CreateMeeting::new);

        Arbitrary<MeetingAction> inviteToMeeting = Combinators.combine(
            Arbitraries.integers().between(0, 4),       // userIndex
            Arbitraries.integers().between(0, 9)        // meetingIndex
        ).as(MeetingAction.InviteToMeeting::new);

        Arbitrary<MeetingAction> acceptInvite = Combinators.combine(
            Arbitraries.integers().between(0, 4),       // userIndex
            Arbitraries.integers().between(0, 9)        // meetingIndex
        ).as(MeetingAction.AcceptInvite::new);

        Arbitrary<MeetingAction> rejectInvite = Combinators.combine(
            Arbitraries.integers().between(0, 4),       // userIndex
            Arbitraries.integers().between(0, 9)        // meetingIndex
        ).as(MeetingAction.RejectInvite::new);

        return Arbitraries.frequencyOf(
            // More creates so we build up state, then a mix of invite/accept/reject
            Tuple.of(4, createMeeting),
            Tuple.of(3, inviteToMeeting),
            Tuple.of(2, acceptInvite),
            Tuple.of(1, rejectInvite)
        ).list().ofMinSize(5).ofMaxSize(30);
    }

    @Property(tries = 200)
    void noPersonInTwoOverlappingMeetings(
        @ForAll("meetingActions") List<MeetingAction> actions,
        @ForAll("userCount") int userCount
    ) {
        // --- Setup: create between 2 and 5 users ---
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            var user = userService.createUser("user-" + i);
            userIds.add(user.id());
        }

        List<Long> meetingIds = new ArrayList<>();

        var baseTime = OffsetDateTime.of(2025, 6, 15, 8, 0, 0, 0, ZoneOffset.UTC);

        // --- Execute actions ---
        for (var action : actions) {
            try {
                switch (action) {
                    case MeetingAction.CreateMeeting cm -> {
                        long userId = userIds.get(cm.userIndex() % userIds.size());
                        var from = baseTime.plusMinutes(cm.startMinutes());
                        var to = from.plusMinutes(cm.durationMinutes());
                        var meeting = meetingsService.createMeeting(
                            cm.name(), userId, from, to
                        );
                        meetingIds.add(meeting.id());
                    }
                    case MeetingAction.InviteToMeeting inv -> {
                        if (meetingIds.isEmpty()) {
                            continue;
                        }
                        long userId = userIds.get(inv.userIndex() % userIds.size());
                        long meetingId = meetingIds.get(
                            inv.meetingIndex() % meetingIds.size()
                        );
                        meetingsService.invite(meetingId, userId);
                    }
                    case MeetingAction.AcceptInvite acc -> {
                        if (meetingIds.isEmpty()) {
                            continue;
                        }
                        long userId = userIds.get(acc.userIndex() % userIds.size());
                        long meetingId = meetingIds.get(
                            acc.meetingIndex() % meetingIds.size()
                        );
                        meetingsService.accept(meetingId, userId);
                    }
                    case MeetingAction.RejectInvite rej -> {
                        if (meetingIds.isEmpty()) {
                            continue;
                        }
                        long userId = userIds.get(rej.userIndex() % userIds.size());
                        long meetingId = meetingIds.get(
                            rej.meetingIndex() % meetingIds.size()
                        );
                        meetingsService.reject(meetingId, userId);
                    }
                }
            } catch (OverlappingMeetingsException e) {
                // This is expected — the system correctly prevented an overlap
            }
        }

        // --- Verify the invariant ---
        // For every user, fetch their confirmed meetings and assert none overlap
        for (long userId : userIds) {
            var confirmedMeetings = meetingRepository.findAllConfirmedMeetingsForUser(userId);
            assertNoOverlap(userId, confirmedMeetings);
        }
    }

    @Provide
    Arbitrary<Integer> userCount() {
        return Arbitraries.integers().between(2, 5);
    }

    /**
     * Asserts that no two meetings in the list overlap in time.
     */
    private void assertNoOverlap(long userId, List<Meeting> meetings) {
        var sorted = meetings.stream()
            .sorted((a, b) -> a.startAt().compareTo(b.startAt()))
            .toList();

        for (int i = 0; i < sorted.size() - 1; i++) {
            var current = sorted.get(i);
            var next = sorted.get(i + 1);

            assertThat(current.endAt())
                .as("User %d: meeting '%s' [%s - %s] must not overlap with '%s' [%s - %s]",
                    userId,
                    current.name(), current.startAt(), current.endAt(),
                    next.name(), next.startAt(), next.endAt())
                .isBeforeOrEqualTo(next.startAt());
        }
    }
}
