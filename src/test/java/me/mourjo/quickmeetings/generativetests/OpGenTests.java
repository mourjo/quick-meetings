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
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.generativetests.Inputs.MAction;
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
    void invariant(@ForAll("meetingInputs") List<Inputs> meetingInputList) {
        meetingState = init();

        for (Inputs meetingInputs : meetingInputList) {
            switch (meetingInputs.action) {
                case CREATE -> createMeeting(meetingInputs);
                case INVITE -> inviteToMeeting(meetingInputs);
                case ACCEPT -> acceptMeetingInvite(meetingInputs);
            }

            meetingState.assertNoUserHasOverlappingMeetings();
        }

    }

    private void acceptMeetingInvite(Inputs meetingInputs) {
        actionOnInvite(meetingInputs,
            (userId, meetingId) -> meetingState.recordAcceptance(userId, meetingId)
        );
    }

    private void inviteToMeeting(Inputs meetingInputs) {
        actionOnInvite(meetingInputs,
            (userId, meetingId) -> meetingState.recordInvitation(userId, meetingId)
        );
    }

    private void actionOnInvite(Inputs meetingInputs, BiFunction<Long, Long, Boolean> action) {
        var user = users.get(meetingInputs.userIdx % users.size());
        var meetings = meetingState.getAllMeetings();

        if (!meetings.isEmpty()) {
            int meetingIndex = meetingInputs.meetingIdx % meetings.size();
            var meeting = meetings.get(meetingIndex);
            action.apply(user.id(), meeting.id());
            meetingInputs.setUser(user);
            meetingInputs.setMeeting(meeting);
        }
    }

    private void createMeeting(Inputs meetingInputs) {
        var user = users.get(meetingInputs.userIdx % users.size());
        meetingInputs.setUser(user);

        var from = LOWER_BOUND_TS.plusMinutes(meetingInputs.startOffsetMins);
        var to = from.plusMinutes(meetingInputs.durationMins);
        var id = meetingState.recordCreation(user, from, to);
        meetingInputs.setCreatedMeetingId(id);
    }

    @Provide
    ListArbitrary<Inputs> meetingInputs() {

        var durationMins = Arbitraries.integers().between(1, 60);
        var startOffsetMins = Arbitraries.integers().between(1, 60);
        var meetingIdxGen = Arbitraries.integers().greaterOrEqual(0);
        var userIdxGen = Arbitraries.integers().greaterOrEqual(0);
        var axn = Arbitraries.of(
            MAction.ACCEPT,
            MAction.CREATE,
            MAction.INVITE
        );

        return Combinators.combine(
            axn, durationMins, startOffsetMins, meetingIdxGen, userIdxGen
        ).as(Inputs::new).list();
    }

}

class Inputs {

    public MAction action;
    public int durationMins;
    public int startOffsetMins;
    public int meetingIdx;
    public int userIdx;
    Long createdMeetingId;

    User user;
    Meeting meeting;

    public Inputs(MAction action, int durationMins, int startOffsetMins, int meetingIdx,
        int userIdx) {
        this.action = action;
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

        if (action == MAction.CREATE) {
            return "Inputs{" +
                "action=" + action +
                ", user=" + (user == null ? "" : user.name()) +
                ", from=" + from +
                ", to=" + to +
                ", createdId=" + (createdMeetingId == null ? "" : createdMeetingId) +
                '}';

        } else {

            return "Inputs{" +
                "action=" + action +
                ", user=" + (user == null ? "" : user.name()) +
                ", meeting=" + (meeting == null ? "" : meeting.id()) +
                '}';

        }

    }

    enum MAction {
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

    List<User> getAvailableUsers() {
        return users;
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