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
import java.util.function.BiFunction;
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

@Tag("test-being-demoed")
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

//    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
//    void noOperationCausesAnOverlap(@ForAll("meetingOperations") List<MeetingOperation> ops) {
//        var state = init();
//        executeOperations(state, ops, state::assertNoUserHasOverlappingMeetings);
//    }


    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void checkMyStack(@ForAll("meetingActions") ActionChain<MeetingState> chain) {
        chain
            .withInvariant(MeetingState::assertNoUserHasOverlappingMeetings)
            .run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {

        return ActionChain.startWith(this::init)
            .withAction(new CreateAction())
            .withAction(new InviteAction())
            .withAction(new AcceptInviteAction())
            //.improveShrinkingWith(MeetingStateChangesDetector::new)
            ;
    }

    void executeOperations(MeetingState state, List<MeetingOperation> ops, Runnable invariant) {
        for (var operation : ops) {
            switch (operation.operationType()) {
                case CREATE -> createMeeting(state, operation);
                case INVITE -> inviteToMeeting(state, operation);
                case ACCEPT -> acceptMeetingInvite(state, operation);
            }
            invariant.run();
        }
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

    private void acceptMeetingInvite(MeetingState state, MeetingOperation operation) {
        actionOnInvite(operation, state, state::recordAcceptance);
    }

    private void rejectMeetingInvite(MeetingState state, MeetingOperation operation) {
        actionOnInvite(operation, state, state::recordRejection);
    }

    private void inviteToMeeting(MeetingState state, MeetingOperation operation) {
        actionOnInvite(operation, state, state::recordInvitation);
    }

    private void actionOnInvite(MeetingOperation operation, MeetingState state,
        BiFunction<Long, Long, Boolean> action) {
        var user = operation.user();
        var meetings = state.getAllMeetings();

        if (!meetings.isEmpty()) {
            int meetingIndex = operation.meetingIdx() % meetings.size();
            var meeting = meetings.get(meetingIndex);
            action.apply(user.id(), meeting.id());
            operation.setUser(user);
            operation.setMeeting(meeting);
        }
    }

    private void createMeeting(MeetingState state, MeetingOperation operation) {
        var user = operation.user();
        var from = LOWER_BOUND_TS.plusMinutes(operation.startOffsetMins());
        var to = from.plusMinutes(operation.durationMins());
        var id = state.recordCreation(user, from, to);
        operation.setCreatedMeetingId(id);
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

    class MeetingStateChangesDetector implements ChangeDetector<MeetingState> {

        @Override
        public void before(MeetingState before) {

        }

        @Override
        public boolean hasChanged(MeetingState after) {
            return false;
        }
    }

    class CreateAction implements Action.Independent<MeetingState> {

        @Override
        public Arbitrary<Transformer<MeetingState>> transformer() {
            var user = Arbitraries.of(users);
            var operationType = Arbitraries.of(OperationType.values());

            var meetingIdx = Arbitraries.integers().greaterOrEqual(0);

            var durationMins = Arbitraries.integers().between(1, 60);
            var startOffsetMins = Arbitraries.integers().between(1, 60);

            var op = Combinators.combine(
                operationType, durationMins, startOffsetMins, meetingIdx, user
            ).as(MeetingOperation::new);

            return op.map(operation -> Transformer.mutate(
                "creation",
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
            var operationType = Arbitraries.of(OperationType.values());

            var meetingIdx = Arbitraries.integers().greaterOrEqual(0);

            var durationMins = Arbitraries.integers().between(1, 60);
            var startOffsetMins = Arbitraries.integers().between(1, 60);

            var op = Combinators.combine(
                operationType, durationMins, startOffsetMins, meetingIdx, user
            ).as(MeetingOperation::new);

            return op.map(operation -> Transformer.mutate(
                "invite",
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
            var operationType = Arbitraries.of(OperationType.values());

            var meetingIdx = Arbitraries.integers().greaterOrEqual(0);

            var durationMins = Arbitraries.integers().between(1, 60);
            var startOffsetMins = Arbitraries.integers().between(1, 60);

            var op = Combinators.combine(
                operationType, durationMins, startOffsetMins, meetingIdx, user
            ).as(MeetingOperation::new);

            return op.map(operation -> Transformer.mutate(
                "accept-invite",
                meetingState -> {
                    actionOnInvite(operation, meetingState, meetingState::recordAcceptance);
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
    private Long createdMeetingId;
    private User user;
    private Meeting meeting;

    MeetingOperation(OperationType operationType, int durationMins, int startOffsetMins,
        int meetingIdx, User user) {
        this.operationType = operationType;
        this.durationMins = durationMins;
        this.startOffsetMins = startOffsetMins;
        this.meetingIdx = meetingIdx;
        this.user = user;

    }

    void setCreatedMeetingId(Long createdMeetingId) {
        if (this.createdMeetingId == null && createdMeetingId != null) {
            this.createdMeetingId = createdMeetingId;
        }
    }

    void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    void setUser(User user) {
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
                ", createdId=" + (createdMeetingId == null ? "" : createdMeetingId) +
                '}';
        } else {
            return "Inputs{" +
                "action=" + operationType +
                ", user=" + (user == null ? "" : user.name()) +
                ", meeting=" + (meeting == null ? "" : meeting.id()) +
                '}';
        }
    }

    enum OperationType {
        CREATE, INVITE, ACCEPT
    }
}

class MeetingState {

    private final UserMeetingRepository userMeetingRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingsService meetingsService;
    private final List<User> users;
    private final Map<Long, Meeting> idToMeeting;
    private final Map<Long, Set<Meeting>> userToConfirmedMeetings = new HashMap<>();

    public MeetingState(MeetingsService meetingsService,
        UserMeetingRepository userMeetingRepository, MeetingRepository meetingRepository,
        List<User> users) {

        this.userMeetingRepository = userMeetingRepository;
        this.meetingRepository = meetingRepository;
        this.meetingsService = meetingsService;
        this.users = users;

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

    Long recordCreation(User user, OffsetDateTime from, OffsetDateTime to) {
        try {
            var meeting = meetingsService.createMeeting(
                "meeting-" + UUID.randomUUID(),
                user.id(),
                from,
                to
            );

            idToMeeting.put(meeting.id(), meeting);
            userToConfirmedMeetings.get(user.id()).add(meeting);
            return meeting.id();
        } catch (OverlappingMeetingsException ignored) {
        }
        return null;
    }

    boolean recordInvitation(long userId, long meetingId) {
        try {
            var invitees = meetingsService.invite(meetingId, userId);
            if (!invitees.isEmpty()) {
                return true;
            }
        } catch (OverlappingMeetingsException ignored) {
        }
        return false;
    }


    boolean recordAcceptance(long userId, long meetingId) {
        try {
            if (meetingsService.accept(meetingId, userId)) {
                var meeting = idToMeeting.get(meetingId);
                userToConfirmedMeetings.get(userId).add(meeting);
                return true;
            }
        } catch (OverlappingMeetingsException ignored) {

        }
        return false;
    }

    boolean recordRejection(long userId, long meetingId) {
        if (meetingsService.reject(meetingId, userId)) {
            var meeting = idToMeeting.get(meetingId);
            userToConfirmedMeetings.get(userId).remove(meeting);
            return true;
        }

        return false;
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
}