package me.mourjo.quickmeetings;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Property-based test that verifies the invariant: a person must never be part of two meetings that overlap in time. Uses jqwik's ActionChain API for stateful
 * testing, generating random sequences of meeting operations and checking the no-overlap invariant after every action.
 */
@SpringBootTest
@JqwikSpringSupport
class NoOverlappingMeetingsPropertyTest {

    static final OffsetDateTime BASE_TIME =
        OffsetDateTime.of(2025, 6, 15, 8, 0, 0, 0, ZoneOffset.UTC);
    @Autowired
    MeetingsService meetingsService;
    @Autowired
    UserService userService;
    @Autowired
    MeetingRepository meetingRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * Clean all data after each try so that each generated test case starts fresh.
     */
    @BeforeProperty
    void cleanUp(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM user_meetings");
        jdbcTemplate.execute("DELETE FROM meetings");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Property(tries = 200, afterFailure = AfterFailureMode.RANDOM_SEED)
    void noPersonInTwoOverlappingMeetings(
        @ForAll("meetingActionChain") ActionChain<MeetingSystemState> chain
    ) {
        chain
            .withInvariant("no person in two overlapping meetings", state -> {
                for (long userId : state.userIds) {
                    var confirmedMeetings =
                        state.meetingRepository.findAllConfirmedMeetingsForUser(userId);
                    assertNoOverlap(userId, confirmedMeetings);
                }
            })
            .run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingSystemState>> meetingActionChain() {
        return ActionChain.startWith(() -> {
                cleanUp(jdbcTemplate);
                // Create 2-5 users as initial state
                int userCount = 3;
                List<Long> userIds = new ArrayList<>();
                for (int i = 0; i < userCount; i++) {
                    var user = userService.createUser("user-" + i);
                    userIds.add(user.id());
                }
                return new MeetingSystemState(userIds, meetingsService, meetingRepository);
            })
            .withAction(createMeetingAction())
            .withAction(inviteAction())
            .withAction(acceptAction())
            .withAction(rejectAction())
            ;
    }

    /**
     * Action: create a meeting owned by a random user at a random time window.
     */
    private Action<MeetingSystemState> createMeetingAction() {
        return new Action.Independent<MeetingSystemState>() {
            @Override
            public Arbitrary<Transformer<MeetingSystemState>> transformer() {
                return Combinators.combine(
                    Arbitraries.integers().between(0, 2),
                    Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                    Arbitraries.integers().between(0, 100),
                    Arbitraries.integers().between(15, 115)
                ).as((userIdx, name, startMin, durMin) ->
                    Transformer.transform(
                        "CreateMeeting[user=%d, name=%s, start=+%dmin, dur=%dmin]"
                            .formatted(userIdx, name, startMin, durMin),
                        s -> {
                            long userId = s.userIds.get(userIdx % s.userIds.size());
                            var from = BASE_TIME.plusMinutes(startMin);
                            var to = from.plusMinutes(durMin);
                            try {
                                var meeting = s.meetingsService.createMeeting(
                                    name, userId, from, to
                                );
                                s.meetingIds.add(meeting.id());
                            } catch (OverlappingMeetingsException e) {
                                // Expected — system correctly prevented overlap
                            }
                            return s;
                        }
                    )
                );
            }
        };
    }

    /**
     * Action: invite a random user to a random existing meeting.
     */
    private Action<MeetingSystemState> inviteAction() {
        return new Action.Independent<MeetingSystemState>() {
            @Override
            public Arbitrary<Transformer<MeetingSystemState>> transformer() {
                return Combinators.combine(
                    Arbitraries.integers().between(0, 2),
                    Arbitraries.integers().between(0, 9)
                ).as((userIdx, meetingIdx) ->
                    Transformer.transform(
                        "Invite[user=%d, meeting=%d]".formatted(userIdx, meetingIdx),
                        s -> {
                            if (s.meetingIds.isEmpty()) {
                                return s;
                            }
                            long userId = s.userIds.get(userIdx % s.userIds.size());
                            long meetingId = s.meetingIds.get(
                                meetingIdx % s.meetingIds.size()
                            );
                            try {
                                s.meetingsService.invite(meetingId, userId);
                            } catch (OverlappingMeetingsException e) {
                                // Expected
                            }
                            return s;
                        }
                    )
                );
            }
        };
    }

    /**
     * Action: accept a meeting invitation.
     */
    private Action<MeetingSystemState> acceptAction() {
        return new Action.Independent<MeetingSystemState>() {
            @Override
            public Arbitrary<Transformer<MeetingSystemState>> transformer() {
                return Combinators.combine(
                    Arbitraries.integers().between(0, 2),
                    Arbitraries.integers().between(0, 9)
                ).as((userIdx, meetingIdx) ->
                    Transformer.transform(
                        "Accept[user=%d, meeting=%d]".formatted(userIdx, meetingIdx),
                        s -> {
                            if (s.meetingIds.isEmpty()) {
                                return s;
                            }
                            long userId = s.userIds.get(userIdx % s.userIds.size());
                            long meetingId = s.meetingIds.get(
                                meetingIdx % s.meetingIds.size()
                            );
                            try {
                                s.meetingsService.accept(meetingId, userId);
                            } catch (OverlappingMeetingsException e) {
                                // Expected
                            }
                            return s;
                        }
                    )
                );
            }
        };
    }

    /**
     * Action: reject a meeting invitation.
     */
    private Action<MeetingSystemState> rejectAction() {
        return new Action.Independent<MeetingSystemState>() {
            @Override
            public Arbitrary<Transformer<MeetingSystemState>> transformer() {
                return Combinators.combine(
                    Arbitraries.integers().between(0, 2),
                    Arbitraries.integers().between(0, 9)
                ).as((userIdx, meetingIdx) ->
                    Transformer.transform(
                        "Reject[user=%d, meeting=%d]".formatted(userIdx, meetingIdx),
                        s -> {
                            if (s.meetingIds.isEmpty()) {
                                return s;
                            }
                            long userId = s.userIds.get(userIdx % s.userIds.size());
                            long meetingId = s.meetingIds.get(
                                meetingIdx % s.meetingIds.size()
                            );
                            s.meetingsService.reject(meetingId, userId);
                            return s;
                        }
                    )
                );
            }
        };
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

    /**
     * Mutable state model tracked through the action chain. Holds references to created user and meeting IDs, plus the Spring services needed to execute
     * actions.
     */
    static class MeetingSystemState {

        final List<Long> userIds;
        final List<Long> meetingIds;
        final MeetingsService meetingsService;
        final MeetingRepository meetingRepository;

        MeetingSystemState(List<Long> userIds, MeetingsService meetingsService,
            MeetingRepository meetingRepository) {
            this.userIds = userIds;
            this.meetingIds = new ArrayList<>();
            this.meetingsService = meetingsService;
            this.meetingRepository = meetingRepository;
        }
    }
}
