package me.mourjo.quickmeetings.generativetests;

import static me.mourjo.quickmeetings.generativetests.OpGenTests.LOWER_BOUND_TS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
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
import net.jqwik.api.Tuple;
import net.jqwik.api.Tuple.Tuple3;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.ChangeDetector;
import net.jqwik.api.state.Transformer;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@JqwikSpringSupport
@SpringBootTest
public class OpGenTests {

    public static final OffsetDateTime LOWER_BOUND_TS = LocalDateTime.of(
        2025,
        6,
        9,
        10,
        20,
        0,
        0
    ).atOffset(ZoneOffset.UTC);

    @Autowired
    UserMeetingRepository userMeetingRepository;
    @Autowired
    MeetingRepository meetingRepository;
    @Autowired
    MeetingsService meetingsService;
    List<User> users;

    public MeetingState init() {
        var state = new MeetingState(
            meetingsService,
            userMeetingRepository,
            meetingRepository,
            users
        );

        // todo remove this initial seed - test for invitations separately

        return state;

    }

    @BeforeProperty
    void createUsers(@Autowired UserService userService, @Autowired UserRepository userRepository,
        @Autowired MeetingRepository meetingRepository,
        @Autowired UserMeetingRepository userMeetingRepository,
        @Autowired JdbcTemplate jdbcTemplate) {
        userMeetingRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();
        User alice = userService.createUser("alice");
        User bob = userService.createUser("bob");
        User charlie = userService.createUser("charlie");
        users = List.of(alice, bob, charlie);
    }

    // (shrinking = ShrinkingMode.FULL, afterFailure = AfterFailureMode.RANDOM_SEED)
    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void invariant(@ForAll("meetingActions") ActionChain<MeetingState> chain) {

        chain.withInvariant(MeetingState::assertNoUserHasOverlappingMeetings).run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {
        return ActionChain.startWith(this::init)
            .withAction(new CreateMeetingAction())
//            .withAction(new AcceptInvitationAction())
            .withAction(new InviteAction())
            .improveShrinkingWith(MeetingStateChangesDetector::new)
            .withMaxTransformations(10)
            ;
    }
}

class MeetingStateChangesDetector implements ChangeDetector<MeetingState> {

    @Override
    public void before(MeetingState before) {

    }

    @Override
    public boolean hasChanged(MeetingState after) {
        return after.isLastChangeImpacted();
    }
}


class InviteAction implements Action.Independent<MeetingState> {

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer() {
        var meetingIdxGen = Arbitraries.integers().greaterOrEqual(0);
        var userIdxGen = Arbitraries.integers().greaterOrEqual(0);

        return Combinators.combine(userIdxGen, meetingIdxGen).as(Tuple::of).map(
            element -> Transformer.mutate(
                "Creating invitation",
                state -> {
                    var users = state.getAvailableUsers();
                    int userIndex = element.get1() % users.size();
                    var user = state.getAvailableUsers().get(userIndex);

                    var meetings = state.getAllMeetings();
                    int meetingIndex = element.get2() % meetings.size();
                    var meeting = meetings.get(meetingIndex);

                    state.recordInvitation(
                        user,
                        meeting.id()
                    );
                }

            )
        );
    }

    @Override
    public boolean precondition(MeetingState state) {
        return state.getMeetingCount() > 0;
    }
}

class AcceptInvitationAction implements Action.Independent<MeetingState> {

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer() {
        var meetingIdxGen = Arbitraries.integers().greaterOrEqual(0);
        var userIdxGen = Arbitraries.integers().greaterOrEqual(0);

        return Combinators.combine(userIdxGen, meetingIdxGen).as(Tuple::of).map(
            element -> Transformer.mutate(
                "Accepting invitation",
                state -> {
                    var users = state.getAvailableUsers();
                    int userIndex = element.get1() % users.size();
                    var user = state.getAvailableUsers().get(userIndex);

                    var meetings = state.getAllMeetings();
                    int meetingIndex = element.get2() % meetings.size();
                    var meeting = meetings.get(meetingIndex);

                    state.recordAcceptance(user.id(), meeting.id());
                }

            )
        );
    }

    @Override
    public boolean precondition(MeetingState state) {
        return state.getMeetingCount() > 0;
    }
}


class CreateMeetingAction implements Action.Independent<MeetingState> {

    int maxOffsetMins, maxDurationMins;

    public CreateMeetingAction() {
        this.maxDurationMins = 60;
        this.maxOffsetMins = 60;
    }

    Arbitrary<Tuple3<Integer, Integer, Integer>> meetingInputs() {
        var durationMins = Arbitraries.integers().between(1, maxDurationMins);
        var startOffsetMins = Arbitraries.integers().between(1, maxOffsetMins);

        return Combinators.combine(
            Arbitraries.integers().greaterOrEqual(0),
            startOffsetMins,
            durationMins
        ).as(Tuple::of);
    }

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer() {
        return meetingInputs()
            .map(tuple -> Transformer.mutate(
                    String.format("user-%s is creating a meeting from [%s] to [%s]",
                        tuple.get2(),
                        LOWER_BOUND_TS.plusMinutes(tuple.get3()),
                        LOWER_BOUND_TS.plusMinutes(tuple.get3() + tuple.get3())),
                    state -> {
                        var users = state.getAvailableUsers();
                        int userIndex = tuple.get1() % users.size();
                        var user = users.get(userIndex);

                        var from = LOWER_BOUND_TS.plusMinutes(tuple.get2());
                        var to = LOWER_BOUND_TS.plusMinutes(tuple.get2() + tuple.get3());

                        state.recordCreation(user, from, to);
                    }
                )
            );
    }
}

class MeetingState {

    private final MeetingsService meetingsService;
    private final List<User> users;
    private final List<Meeting> meetings;
    private final Map<Long, Meeting> idToMeeting;

    private final Map<Long, Set<Meeting>> userToConfirmedMeetings = new HashMap<>();
    private boolean lastChangeImpacted = false;

    public MeetingState(MeetingsService meetingsService,
        UserMeetingRepository userMeetingRepository, MeetingRepository meetingRepository,
        List<User> users) {

        this.meetingsService = meetingsService;
        this.users = users;

        meetings = new ArrayList<>();
        idToMeeting = new HashMap<>();
        meetingRepository.deleteAll();

        userMeetingRepository.deleteAll();

        for (User u : users) {
            userToConfirmedMeetings.putIfAbsent(u.id(), new TreeSet<>((o1, o2) -> {
                if (o1.id() == o2.id()) {
                    return 0;
                }
                if (o1.startAt().isEqual(o2.startAt())) {
                    return Long.compare(o1.id(), o2.id());
                }
                return o1.startAt().compareTo(o2.startAt());
            }));
        }
    }

    void recordCreation(User user, OffsetDateTime from, OffsetDateTime to) {
        try {
            var meeting = meetingsService.createMeeting(
                "name-" + UUID.randomUUID(),
                user.id(),
                from,
                to
            );

            meetings.add(meeting);
            idToMeeting.put(meeting.id(), meeting);
            userToConfirmedMeetings.get(user.id()).add(meeting);

            lastChangeImpacted = true;
        } catch (OverlappingMeetingsException ignored) {
            lastChangeImpacted = false;
        }
    }

    boolean recordInvitation(User user, long meetingId) {
        try {
            var invitees = meetingsService.invite(meetingId, user.id());
            if (!invitees.isEmpty()) {
                lastChangeImpacted = true;
                return true;
            }
        } catch (OverlappingMeetingsException ignored) {
        }
        lastChangeImpacted = false;
        return false;
    }


    boolean recordAcceptance(long userId, long meetingId) {
        if (meetingsService.accept(meetingId, userId)) {
            var meeting = idToMeeting.get(meetingId);
            userToConfirmedMeetings.get(userId).add(meeting);
            lastChangeImpacted = true;
            return true;
        }
        lastChangeImpacted = false;
        return false;
    }

    boolean isLastChangeImpacted() {
        return lastChangeImpacted;
    }

    List<User> getAvailableUsers() {
        return users;
    }

    int getMeetingCount() {
        return meetings.size();
    }

    void assertNoUserHasOverlappingMeetings() {
        assertThat(hasOverlap()).isFalse();
    }

    List<Meeting> getAllMeetings() {
        meetings.sort(new Comparator<Meeting>() {
            @Override
            public int compare(Meeting o1, Meeting o2) {
                var duration1 = Duration.between(o1.startAt(), o1.endAt());
                var duration2 = Duration.between(o2.startAt(), o2.endAt());
//                return duration2.compareTo(duration1);

                int n = o1.startAt().compareTo(o2.startAt());
                return n != 0 ? n : duration1.compareTo(duration2);

            }
        });
        return meetings;
    }

    private boolean hasOverlap() {
        for (long userId : userToConfirmedMeetings.keySet()) {
            var prevEnd = LOWER_BOUND_TS.minusYears(10);
            for (var meeting : userToConfirmedMeetings.get(userId)) {
                if (meeting.startAt().isEqual(prevEnd) || meeting.startAt().isBefore(prevEnd)) {
                    return true;
                }
                prevEnd = prevEnd.isBefore(meeting.endAt()) ? meeting.endAt() : prevEnd;
            }
        }
        return false;
    }
}