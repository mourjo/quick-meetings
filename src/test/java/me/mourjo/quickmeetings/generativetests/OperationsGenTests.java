package me.mourjo.quickmeetings.generativetests;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static me.mourjo.quickmeetings.generativetests.MeetingOperation.LOWER_BOUND_TS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.generativetests.MeetingOperation.OperationType;
import me.mourjo.quickmeetings.generativetests.MeetingState.MeetingStateChangesDetector;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.SortedMeetingSet;
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
import net.jqwik.api.state.ChangeDetector;
import net.jqwik.api.state.Transformer;
import net.jqwik.api.statistics.Statistics;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

@JqwikSpringSupport
@SpringBootTest
public class OperationsGenTests {

    UserMeetingRepository userMeetingRepository;
    MeetingRepository meetingRepository;
    MeetingsService meetingsService;
    UserRepository userRepository;
    List<User> users;
    JdbcClient jdbcClient;

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void noOperationCausesAnOverlap(@ForAll("meetingActions") ActionChain<MeetingState> chain) {
        var finalState = chain
            .withInvariant(MeetingState::assertNoUserHasOverlappingMeetings)
            .run();

        collectStatistics(finalState);
    }

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void noOperationCausesEmptyMeetings(@ForAll("meetingActions") ActionChain<MeetingState> chain) {
        chain
            .withInvariant(MeetingState::assertEveryMeetingHasOneConfirmedAttendee)
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
            users,
            jdbcClient
        );
    }

    private void collectStatistics(MeetingState meetingState) {
        for (var action : meetingState.getHistoricalOperations()) {
            Statistics.label("action").collect(action.operationType());
        }
    }

    @BeforeProperty
    void createUsers(@Autowired UserService userService, @Autowired UserRepository userRepository,
        @Autowired MeetingRepository meetingRepository,
        @Autowired UserMeetingRepository userMeetingRepository,
        @Autowired MeetingsService meetingsService,
        @Autowired JdbcTemplate jdbcTemplate,
        @Autowired DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
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


record MeetingOperation(
    int currentMeetingCount, // the number of meetings is used only for making the output more readable
    OperationType operationType,
    int startOffsetMins,
    int durationMins,
    int meetingIdx,
    User user) {

    public static final OffsetDateTime LOWER_BOUND_TS = LocalDateTime.of(2025, 6, 9, 10, 20, 0, 0)
        .atOffset(ZoneOffset.UTC);

    @Override
    public String toString() {
        var from = LOWER_BOUND_TS.plusMinutes(startOffsetMins);
        var to = LOWER_BOUND_TS.plusMinutes(startOffsetMins + durationMins);

        if (operationType == MeetingOperation.OperationType.CREATE) {
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
                ", meetingIdx=" + (currentMeetingCount == 0 ? -1 : meetingIdx % currentMeetingCount) +
                '}';
        }
    }

    enum OperationType {
        CREATE, INVITE, ACCEPT, REJECT
    }
}

class MeetingState {

    public static MeetingState INST;

    private final MeetingsService meetingsService;
    private final Map<Long, Meeting> idToMeeting;
    private final Map<Long, Set<Meeting>> userToConfirmedMeetings = new HashMap<>();
    private final JdbcClient jdbcClient;
    private final AtomicReference<MeetingOperation> lastOperation;
    private final List<MeetingOperation> historicalOperations = new ArrayList<>();

    public MeetingState(MeetingsService meetingsService,
        UserMeetingRepository userMeetingRepository, MeetingRepository meetingRepository,
        List<User> users, JdbcClient jdbcClient) {

        this.meetingsService = meetingsService;
        this.jdbcClient = jdbcClient;
        this.lastOperation = new AtomicReference<>();

        idToMeeting = new TreeMap<>();

        meetingRepository.deleteAll();
        userMeetingRepository.deleteAll();

        for (User user : users) {
            userToConfirmedMeetings.putIfAbsent(user.id(), SortedMeetingSet.create());
        }

        INST = this;
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

    public List<MeetingOperation> getHistoricalOperations() {
        return historicalOperations;
    }

    void performAction(MeetingOperation operation) {
        historicalOperations.add(operation);
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
        assertThat(hasOverlap()).as("No action causes overlapping meetings").isFalse();
    }

    void assertEveryMeetingHasOneConfirmedAttendee() {
        var attendeeCount = jdbcClient.sql("""
                select count(*)
                from user_meetings
                where meeting_id NOT IN (
                     select meeting_id from user_meetings where role_of_user IN ('OWNER', 'ACCEPTED')
                );
                """)
            .query(Integer.class)
            .single();

        assertThat(attendeeCount).as("No meeting should have zero attendees").isEqualTo(0);
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

        private MeetingOperation operation;

        @Override
        public void before(MeetingState before) {
            operation = before.getLastOperation();
        }

        @Override
        public boolean hasChanged(MeetingState after) {
            return after.getLastOperation() != this.operation;
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

        // the number of meetings is used only for making the output more readable
        // this would not be required for Dependent actions but that makes the test slower
        var numberOfMeetings = Arbitraries.just(MeetingState.INST.getAllMeetings().size());

        return Combinators.combine(
                numberOfMeetings, operationType, startOffsetMins, durationMins, meetingIdx, user
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

