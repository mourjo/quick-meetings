package me.mourjo.quickmeetings.generativetests;

import static me.mourjo.quickmeetings.generativetests.OpGenTests.LOWER_BOUND_TS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.generativetests.MeetingOperation.OperationType;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.arbitraries.ListArbitrary;
import net.jqwik.api.lifecycle.BeforeProperty;
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

    UserMeetingRepository userMeetingRepository;
    MeetingRepository meetingRepository;
    MeetingsService meetingsService;
    UserRepository userRepository;
    List<User> users;

    MeetingState meetingState;

    public MeetingState init() {
        var state = new MeetingState(
            meetingsService,
            userMeetingRepository,
            meetingRepository,
            users
        );

        return state;
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
        User debbie = userService.createUser("debbie");
        User erin = userService.createUser("erin");
        users = List.of(alice, bob, charlie, debbie, erin);
    }

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void invariant(@ForAll("meetingOperations") List<MeetingOperation> operations) {
        meetingState = init();

        for (var operation : operations) {
            switch (operation.operationType()) {
                case CREATE -> createMeeting(operation);
                case INVITE -> inviteToMeeting(operation);
//                case ACCEPT -> acceptMeetingInvite(operation);
            }

            meetingState.assertNoUserHasOverlappingMeetings();
        }
    }

    private void acceptMeetingInvite(MeetingOperation operation) {
        actionOnInvite(operation,
            (userId, meetingId) -> meetingState.recordAcceptance(userId, meetingId)
        );
    }

    private void inviteToMeeting(MeetingOperation operation) {
        actionOnInvite(operation,
            (userId, meetingId) -> meetingState.recordInvitation(userId, meetingId)
        );
    }

    private void actionOnInvite(MeetingOperation operation,
        BiFunction<Long, Long, Boolean> action) {
        var user = users.get(operation.userIdx() % users.size());
        var meetings = meetingState.getAllMeetings();

        if (!meetings.isEmpty()) {
            int meetingIndex = operation.meetingIdx() % meetings.size();
            var meeting = meetings.get(meetingIndex);
            action.apply(user.id(), meeting.id());
            operation.setUser(user);
            operation.setMeeting(meeting);
        }
    }

    private void createMeeting(MeetingOperation operation) {
        var user = users.get(operation.userIdx() % users.size());
        operation.setUser(user);

        var from = LOWER_BOUND_TS.plusMinutes(operation.startOffsetMins());
        var to = from.plusMinutes(operation.durationMins());
        var id = meetingState.recordCreation(user, from, to);
        operation.setCreatedMeetingId(id);
    }

    @Provide
    ListArbitrary<MeetingOperation> meetingOperations() {

        var durationMins = Arbitraries.integers().between(1, 60);
        var startOffsetMins = Arbitraries.integers().between(1, 60);
        var meetingIdx = Arbitraries.integers().greaterOrEqual(0);
        var userIdx = Arbitraries.integers().greaterOrEqual(0);
        var operationType = Arbitraries.of(
            OperationType.ACCEPT, OperationType.CREATE, OperationType.INVITE
        );

        return Combinators.combine(
            operationType, durationMins, startOffsetMins, meetingIdx, userIdx
        ).as(MeetingOperation::new).list();
    }

}

@Accessors(fluent = true)
@Getter
class MeetingOperation {

    private final OperationType operationType;
    private final int durationMins;
    private final int startOffsetMins;
    private final int meetingIdx;
    private final int userIdx;
    private Long createdMeetingId;
    private User user;
    private Meeting meeting;

    MeetingOperation(OperationType operationType, int durationMins, int startOffsetMins,
        int meetingIdx,
        int userIdx) {
        this.operationType = operationType;
        this.durationMins = durationMins;
        this.startOffsetMins = startOffsetMins;
        this.meetingIdx = meetingIdx;
        this.userIdx = userIdx;

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

    private final MeetingsService meetingsService;
    private final List<User> users;
    private final List<Meeting> meetings;
    private final Map<Long, Meeting> idToMeeting;
    private final Map<Long, Set<Meeting>> userToConfirmedMeetings = new HashMap<>();

    public MeetingState(MeetingsService meetingsService,
        UserMeetingRepository userMeetingRepository, MeetingRepository meetingRepository,
        List<User> users) {

        this.meetingsService = meetingsService;
        this.users = users;

        meetings = new ArrayList<>();
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

            meetings.add(meeting);
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
        if (meetingsService.accept(meetingId, userId)) {
            var meeting = idToMeeting.get(meetingId);
            userToConfirmedMeetings.get(userId).add(meeting);
            return true;
        }
        return false;
    }

    void assertNoUserHasOverlappingMeetings() {
        assertThat(hasOverlap()).isFalse();
    }

    List<Meeting> getAllMeetings() {
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