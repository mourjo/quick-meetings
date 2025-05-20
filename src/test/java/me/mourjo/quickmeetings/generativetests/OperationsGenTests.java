package me.mourjo.quickmeetings.generativetests;

import static me.mourjo.quickmeetings.generativetests.OperationsGenTests.LOWER_BOUND_TS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeeting;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.generativetests.MeetingOperation.OperationType;
import me.mourjo.quickmeetings.generativetests.MeetingState.MeetingStateChangesDetector;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.arbitraries.ListArbitrary;
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
public class OperationsGenTests {

    public static final OffsetDateTime LOWER_BOUND_TS = LocalDateTime.of(2025, 6, 9, 10, 20, 0, 0)
        .atOffset(ZoneOffset.UTC);
    UserMeetingRepository userMeetingRepository;
    MeetingRepository meetingRepository;
    MeetingsService meetingsService;
    UserRepository userRepository;
    List<User> users;

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    @Tag("test-being-demoed")
    void noOperationCausesAnOverlap(@ForAll("meetingActions") ActionChain<MeetingState> chain) {
        chain
            .withInvariant(MeetingState::assertNoUserHasOverlappingMeetings)
            .run();
    }

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void everyMeetingHasAnOwner(@ForAll("meetingActions") ActionChain<MeetingState> chain) {
        chain
            .withInvariant(MeetingState::assertEveryMeetingHasAnOwner)
            .run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {
        return ActionChain.startWith(this::init)
            .withAction(new CreateAction())
            .withAction(new InviteAction())
            .withAction(new AcceptInviteAction())
            .withAction(new RejectInviteAction())
            .improveShrinkingWith(MeetingStateChangesDetector::new);
    }


    @Provide
    ListArbitrary<MeetingOperation> meetingOperations() {
        var user = Arbitraries.of(users);
        var operationType = Arbitraries.of(OperationType.values());

        var meetingIdx = Arbitraries.integers().greaterOrEqual(0);

        var durationMins = Arbitraries.integers().between(1, 60);
        var startOffsetMins = Arbitraries.integers().between(1, 60);

        return Combinators.combine(
            operationType, durationMins, startOffsetMins, meetingIdx, user
        ).as(MeetingOperation::new).list();
    }

    public MeetingState init() {
        return new MeetingState(
            meetingsService,
            userMeetingRepository,
            meetingRepository,
            users
        );
    }

    private void actionOnInvite(MeetingOperation operation, MeetingState state,
        Consumer<MeetingOperation> action) {

        var meetings = state.getAllMeetings();

        if (!meetings.isEmpty()) {
            action.accept(operation);
        }
    }

    private void createMeeting(MeetingState state, MeetingOperation operation) {
        state.recordCreation(operation);
    }

    @BeforeProperty
    void createUsers(@Autowired UserService userService, @Autowired UserRepository userRepository,
        @Autowired MeetingRepository meetingRepository,
        @Autowired UserMeetingRepository userMeetingRepository,
        @Autowired MeetingsService meetingsService,
        @Autowired JdbcTemplate jdbcTemplate) {
        this.userMeetingRepository = userMeetingRepository;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.meetingsService = meetingsService;

        this.userMeetingRepository.deleteAll();
        this.meetingRepository.deleteAll();
        this.userRepository.deleteAll();

        jdbcTemplate.execute("VACUUM FULL");

        User alice = userService.createUser("alice");
        User bob = userService.createUser("bob");
        User charlie = userService.createUser("charlie");
        users = List.of(alice, bob, charlie);
    }


    class CreateAction implements Action.Independent<MeetingState> {

        @Override
        public Arbitrary<Transformer<MeetingState>> transformer() {
            var user = Arbitraries.of(users);
            var operationType = Arbitraries.of(OperationType.CREATE);

            var meetingIdx = Arbitraries.integers().greaterOrEqual(0);

            var durationMins = Arbitraries.integers().between(1, 60);
            var startOffsetMins = Arbitraries.integers().between(1, 60);

            var op = Combinators.combine(
                operationType, durationMins, startOffsetMins, meetingIdx, user
            ).as(MeetingOperation::new);

            return op.map(operation -> Transformer.mutate(
                operation.toString(),
                state -> {
                    createMeeting(state, operation);
                }
            ));
        }
    }

    class InviteAction implements Action.Independent<MeetingState> {

        @Override
        public Arbitrary<Transformer<MeetingState>> transformer() {
            var user = Arbitraries.of(users);
            var operationType = Arbitraries.of(OperationType.INVITE);

            var meetingIdx = Arbitraries.integers().greaterOrEqual(0);

            var durationMins = Arbitraries.integers().between(1, 60);
            var startOffsetMins = Arbitraries.integers().between(1, 60);

            var op = Combinators.combine(
                operationType, durationMins, startOffsetMins, meetingIdx, user
            ).as(MeetingOperation::new);

            return op.map(operation -> Transformer.mutate(
                operation.toString(),
                meetingState -> {
                    actionOnInvite(operation, meetingState, meetingState::recordInvitation);
                }
            ));
        }
    }

    class AcceptInviteAction implements Action.Independent<MeetingState> {

        @Override
        public Arbitrary<Transformer<MeetingState>> transformer() {
            var user = Arbitraries.of(users);
            var operationType = Arbitraries.of(OperationType.ACCEPT);

            var meetingIdx = Arbitraries.integers().greaterOrEqual(0);

            var durationMins = Arbitraries.integers().between(1, 60);
            var startOffsetMins = Arbitraries.integers().between(1, 60);

            var op = Combinators.combine(
                operationType, durationMins, startOffsetMins, meetingIdx, user
            ).as(MeetingOperation::new);

            return op.map(operation -> Transformer.mutate(
                operation.toString(),
                meetingState -> {
                    actionOnInvite(operation, meetingState, meetingState::recordAcceptance);
                }
            ));
        }
    }

    class RejectInviteAction implements Action.Independent<MeetingState> {

        @Override
        public Arbitrary<Transformer<MeetingState>> transformer() {
            var user = Arbitraries.of(users);
            var operationType = Arbitraries.of(OperationType.REJECT);

            var meetingIdx = Arbitraries.integers().greaterOrEqual(0);

            var durationMins = Arbitraries.integers().between(1, 60);
            var startOffsetMins = Arbitraries.integers().between(1, 60);

            var op = Combinators.combine(
                operationType, durationMins, startOffsetMins, meetingIdx, user
            ).as(MeetingOperation::new);

            return op.map(operation -> Transformer.mutate(
                operation.toString(),
                meetingState -> {
                    actionOnInvite(operation, meetingState, meetingState::recordRejection);
                }
            ));
        }
    }
}

@Accessors(fluent = true)
@Getter
class MeetingOperation {

    private final OperationType operationType;
    private final int durationMins;
    private final int startOffsetMins;
    private final int meetingIdx;
    private final User user;

    MeetingOperation(OperationType operationType, int durationMins, int startOffsetMins,
        int meetingIdx, User user) {
        this.operationType = operationType;
        this.durationMins = durationMins;
        this.startOffsetMins = startOffsetMins;
        this.meetingIdx = meetingIdx;
        this.user = user;

    }


    @Override
    public String toString() {
        var from = LOWER_BOUND_TS.plusMinutes(startOffsetMins);
        var to = LOWER_BOUND_TS.plusMinutes(startOffsetMins + durationMins);

        if (operationType == OperationType.CREATE) {
            return "Inputs{" +
                "action=" + operationType +
                ", user=" + (user == null ? "" : user.name()) +
                ", from=" + from +
                ", to=" + to +
                '}';
        } else {
            return "Inputs{" +
                "action=" + operationType +
                ", user=" + (user == null ? "" : user.name()) +
                ", meetingIdx=" + (meetingIdx) +
                '}';
        }
    }

    enum OperationType {
        CREATE, INVITE, ACCEPT, REJECT
    }
}

class MeetingState {

    private final UserMeetingRepository userMeetingRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingsService meetingsService;
    private final List<User> users;
    private final Map<Long, Meeting> idToMeeting;
    private final Map<Long, Set<Meeting>> userToConfirmedMeetings = new HashMap<>();

    private final AtomicReference<MeetingOperation> lastOperation;

    public MeetingState(MeetingsService meetingsService,
        UserMeetingRepository userMeetingRepository, MeetingRepository meetingRepository,
        List<User> users) {

        this.userMeetingRepository = userMeetingRepository;
        this.meetingRepository = meetingRepository;
        this.meetingsService = meetingsService;
        this.users = users;
        this.lastOperation = new AtomicReference<>();

        idToMeeting = new HashMap<>();

        meetingRepository.deleteAll();
        userMeetingRepository.deleteAll();

        for (User user : users) {
            userToConfirmedMeetings.putIfAbsent(user.id(), new TreeSet<>((o1, o2) -> {
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

    public MeetingOperation getLastOperation() {
        return lastOperation.get();
    }

    private void setLastOperation(MeetingOperation op) {
        lastOperation.set(op);
    }

    void recordCreation(MeetingOperation operation) {
        try {
            var from = LOWER_BOUND_TS.plusMinutes(operation.startOffsetMins());
            var to = from.plusMinutes(operation.durationMins());
            var userId = operation.user().id();
            var meeting = meetingsService.createMeeting(
                "meeting-" + UUID.randomUUID(),
                operation.user().id(),
                from,
                to
            );

            idToMeeting.put(meeting.id(), meeting);
            userToConfirmedMeetings.get(userId).add(meeting);
            setLastOperation(operation);
        } catch (OverlappingMeetingsException ignored) {
        }
    }

    void recordInvitation(MeetingOperation operation) {
        try {
            var user = operation.user();
            var allMeetings = getAllMeetings();
            var idx = operation.meetingIdx();
            var selectedMeeting = allMeetings.get(idx % allMeetings.size());
            var invitees = meetingsService.invite(selectedMeeting.id(), user.id());
            if (!invitees.isEmpty()) {
                setLastOperation(operation);
            }
        } catch (OverlappingMeetingsException ignored) {
        }
    }


    void recordAcceptance(MeetingOperation operation) {
        try {
            var user = operation.user();
            var allMeetings = getAllMeetings();
            var idx = operation.meetingIdx();
            var selectedMeeting = allMeetings.get(idx % allMeetings.size());
            var selectedMeetingId = selectedMeeting.id();

            if (meetingsService.accept(selectedMeetingId, user.id())) {
                var meeting = idToMeeting.get(selectedMeetingId);
                userToConfirmedMeetings.get(user.id()).add(meeting);
                setLastOperation(operation);
            }
        } catch (OverlappingMeetingsException ignored) {

        }
    }

    void recordRejection(MeetingOperation operation) {
        var allMeetings = getAllMeetings();
        var idx = operation.meetingIdx();
        var selectedMeeting = allMeetings.get(idx % allMeetings.size());
        var selectedMeetingId = selectedMeeting.id();
        var userId = operation.user().id();

        if (meetingsService.reject(selectedMeetingId, userId)) {
            var meeting = idToMeeting.get(selectedMeetingId);
            userToConfirmedMeetings.get(userId).remove(meeting);
            setLastOperation(operation);
        }
    }

    void assertNoUserHasOverlappingMeetings() {
        assertThat(hasOverlap()).isFalse();
    }

    void assertEveryMeetingHasAnOwner() {
        var meetingIdsWithOwners = userMeetingRepository.findAll().stream()
            .filter(um -> um.userRole() == RoleOfUser.OWNER)
            .map(UserMeeting::meetingId)
            .collect(Collectors.toSet());

        var meetingIds = meetingRepository.findAll().stream().map(Meeting::id)
            .collect(Collectors.toSet());

        assertThat(meetingIdsWithOwners).isEqualTo(meetingIds);
    }

    List<Meeting> getAllMeetings() {
        return idToMeeting.values().stream().toList();
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

    static class MeetingStateChangesDetector implements ChangeDetector<MeetingState> {

        private MeetingOperation op;

        @Override
        public void before(MeetingState before) {
            op = before.getLastOperation();
        }

        @Override
        public boolean hasChanged(MeetingState after) {
            return after.getLastOperation() != this.op;
        }
    }
}