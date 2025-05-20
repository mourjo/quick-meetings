package me.mourjo.quickmeetings.generativetests;

import static me.mourjo.quickmeetings.generativetests.MeetingOperation.LOWER_BOUND_TS;
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
            .withAction(new CreateAction(users))
            .withAction(new InviteAction(users))
            .withAction(new AcceptInviteAction(users))
            .withAction(new RejectInviteAction(users))
            .improveShrinkingWith(MeetingStateChangesDetector::new);
    }

    public MeetingState init() {
        return new MeetingState(
            meetingsService,
            userMeetingRepository,
            meetingRepository,
            users
        );
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
}

@Accessors(fluent = true)
@Getter
class MeetingOperation {

    public static final OffsetDateTime LOWER_BOUND_TS = LocalDateTime.of(2025, 6, 9, 10, 20, 0, 0)
        .atOffset(ZoneOffset.UTC);
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

    private void recordCreation(MeetingOperation operation) {
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

    void performAction(MeetingOperation operation) {
        switch (operation.operationType()) {
            case CREATE -> recordCreation(operation);
            case ACCEPT -> recordAcceptance(operation);
            case INVITE -> recordInvitation(operation);
            case REJECT -> recordRejection(operation);
        }
    }

    private void recordInvitation(MeetingOperation operation) {
        var allMeetings = getAllMeetings();
        if (allMeetings.isEmpty()) {
            return;
        }
        var user = operation.user();
        var idx = operation.meetingIdx();
        var selectedMeeting = allMeetings.get(idx % allMeetings.size());

        try {
            var invitees = meetingsService.invite(selectedMeeting.id(), user.id());
            if (!invitees.isEmpty()) {
                setLastOperation(operation);
            }
        } catch (OverlappingMeetingsException ignored) {
        }
    }


    private void recordAcceptance(MeetingOperation operation) {
        try {
            var allMeetings = getAllMeetings();
            if (allMeetings.isEmpty()) {
                return;
            }
            var user = operation.user();
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

    private void recordRejection(MeetingOperation operation) {
        var allMeetings = getAllMeetings();
        if (allMeetings.isEmpty()) {
            return;
        }
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

abstract class BaseAction implements Action.Independent<MeetingState> {

    protected List<User> users;

    public BaseAction(List<User> users) {
        this.users = users;
    }

    abstract OperationType getOperationType();

    @Override
    public final Arbitrary<Transformer<MeetingState>> transformer() {
        var user = Arbitraries.of(users);
        var operationType = Arbitraries.just(getOperationType());
        var meetingIdx = Arbitraries.integers().greaterOrEqual(0);
        var durationMins = Arbitraries.integers().between(1, 60);
        var startOffsetMins = Arbitraries.integers().between(1, 60);

        return Combinators.combine(
                operationType, durationMins, startOffsetMins, meetingIdx, user
            ).as(MeetingOperation::new)
            .map(operation -> Transformer.mutate(
                operation.toString(),
                state -> state.performAction(operation)
            ));
    }
}

class CreateAction extends BaseAction {

    public CreateAction(List<User> users) {
        super(users);
    }

    @Override
    OperationType getOperationType() {
        return OperationType.CREATE;
    }
}

class InviteAction extends BaseAction {

    public InviteAction(List<User> users) {
        super(users);
    }

    @Override
    OperationType getOperationType() {
        return OperationType.INVITE;
    }
}

class AcceptInviteAction extends BaseAction {

    public AcceptInviteAction(List<User> users) {
        super(users);
    }

    @Override
    OperationType getOperationType() {
        return OperationType.ACCEPT;
    }
}

class RejectInviteAction extends BaseAction {

    public RejectInviteAction(List<User> users) {
        super(users);
    }

    @Override
    OperationType getOperationType() {
        return OperationType.REJECT;
    }
}

