package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeeting;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
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
import net.jqwik.api.Tuple.Tuple4;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
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
    MeetingsService meetingsService;
    @Autowired
    MeetingRepository meetingRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserMeetingRepository userMeetingRepository;
    @Autowired
    UserService userService;
    User alice, bob, charlie;


    @BeforeTry
    void cleanup(@Autowired UserMeetingRepository userMeetingRepository,
        @Autowired MeetingRepository meetingRepository) {
//        userMeetingRepository.deleteAll();
//        meetingRepository.deleteAll();
    }

    public MeetingState init() {
        userMeetingRepository.deleteAll();
        meetingRepository.deleteAll();

        var state = new MeetingState(
            meetingsService,
            meetingRepository,
            userService,
            userRepository,
            userMeetingRepository,
            List.of(alice, bob, charlie)
        );

        // todo remove this initial seed - test for invitations separately
        state.recordCreation(
            "Pre-test",
            bob,
            LOWER_BOUND_TS.plusMinutes(10),
            LOWER_BOUND_TS.plusMinutes(15)
        );

        return state;

    }

    @BeforeProperty
    void createUsers(@Autowired UserService userService, @Autowired UserRepository userRepository,
        @Autowired MeetingRepository meetingRepository,
        @Autowired UserMeetingRepository userMeetingRepository,
        @Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("VACUUM FULL");
        userMeetingRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();
        alice = userService.createUser("alice");
        bob = userService.createUser("bob");
        charlie = userService.createUser("charlie");

    }

    // (shrinking = ShrinkingMode.FULL)
    @Property
    void invariant(@ForAll("meetingActions") ActionChain<MeetingState> chain) {

        chain.withInvariant(MeetingState::assertNoUserHasOverlappingMeetings).run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {
        return ActionChain.startWith(this::init)
            .withAction(new CreateMeetingAction(LOWER_BOUND_TS, 60, 60))
            //.withAction(new AcceptInvitationAction())
            .withAction(new CreateInvitationAction())
            ;
    }
}


class CreateInvitationAction implements Action.Dependent<MeetingState> {

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer(MeetingState previousState) {
        Arbitrary<Long> meetingIds = Arbitraries.of(
            previousState.getAllMeetingIds());
        Arbitrary<User> users = Arbitraries.of(
            previousState.getAvailableUsers()
        );

        return Combinators.combine(
                meetingIds,
                users
            ).as(Tuple::of)
            .map(tuple -> Transformer.mutate(
                    "Inviting user-%s to meeting-%s".formatted(tuple.get2().id(), tuple.get1()),
                    state -> {
                        var meetingId = tuple.get1();
                        var user = tuple.get2();
                        try {
                            state.recordInvitation(user, meetingId);
                        } catch (MeetingNotFoundException | OverlappingMeetingsException ex) {
                            // ignored
                        }
                    }
                )
            );
    }
}

class AcceptInvitationAction implements Action.Dependent<MeetingState> {

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer(MeetingState previousState) {
        var invitations = Arbitraries.of(previousState.findAllUserMeetings());

        return invitations
            .map(invitation -> Transformer.mutate(
                    "User-%s is accepting meeting-%s".formatted(
                        invitation.userId(),
                        invitation.meetingId()),
                    state -> {
                        try {
                            state.recordAcceptance(invitation);
                        } catch (MeetingNotFoundException ex) {
                            // ignored
                        }
                    }
                )
            );
    }
}


class CreateMeetingAction implements Action.Dependent<MeetingState> {

    OffsetDateTime minStartTime;
    int maxOffsetMins, maxDurationMins;

    public CreateMeetingAction(OffsetDateTime minTime, int maxOffsetMins, int maxDurationMins) {
        this.minStartTime = minTime;
        this.maxDurationMins = maxDurationMins;
        this.maxOffsetMins = maxOffsetMins;
    }

    Arbitrary<Tuple4<String, User, Integer, Integer>> meetingInputs(
        List<User> availableUsers) {
        var durationMins = Arbitraries.integers().between(1, maxDurationMins);
        var startOffsetMins = Arbitraries.integers().between(1, maxOffsetMins);

        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(5),
            Arbitraries.of(availableUsers),
            startOffsetMins,
            durationMins
        ).as(Tuple::of);
    }

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer(MeetingState previousState) {
        return meetingInputs(previousState.getAvailableUsers())
            .map(tuple -> Transformer.mutate(
                    String.format("user-%s-%s is creating a meeting from [%s] to [%s]",
                        tuple.get2().id(),
                        tuple.get2().name(),
                        minStartTime.plusMinutes(tuple.get3()),
                        minStartTime.plusMinutes(tuple.get3() + tuple.get4())),
                    state -> {
                        try {
                            var name = tuple.get1();
                            var user = tuple.get2();
                            var from = minStartTime.plusMinutes(tuple.get3());
                            var to = minStartTime.plusMinutes(tuple.get3() + tuple.get4());
                            state.recordCreation(name, user, from, to);
                        } catch (OverlappingMeetingsException ex) {
                            // ignore
                        }
                    }
                )
            );
    }
}

class MeetingState {

    private final MeetingsService meetingsService;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final UserMeetingRepository userMeetingRepository;
    private final UserService userService;
    private final List<User> users;
    private final List<Meeting> meetings;
    private final Map<Long, Meeting> idToMeeting;

    private final Set<UserMeeting> userMeetings;

    public MeetingState(MeetingsService meetingsService, MeetingRepository meetingRepository,
        UserService userService, UserRepository userRepository,
        UserMeetingRepository userMeetingRepository, List<User> users) {
        this.meetingsService = meetingsService;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.userMeetingRepository = userMeetingRepository;
        this.userService = userService;
        this.users = users;

        meetings = new ArrayList<>();
        idToMeeting = new HashMap<>();

        userMeetings = new TreeSet<>(new Comparator<UserMeeting>() {
            @Override
            public int compare(UserMeeting o1, UserMeeting o2) {
                if (o1.meetingId() == o2.meetingId()) {
                    return Long.compare(o1.userId(), o2.userId());
                }
                return Long.compare(o1.meetingId(), o2.meetingId());
            }
        });
    }

    void recordCreation(String name, User user, OffsetDateTime from, OffsetDateTime to) {
        var meeting = meetingsService.createMeeting(
            name,
            user.id(),
            from,
            to
        );

        meetings.add(meeting);
        idToMeeting.put(meeting.id(), meeting);

        userMeetings.add(UserMeeting.builder()
            .userId(user.id())
            .meetingId(meeting.id())
            .userRole(RoleOfUser.OWNER)
            .build()
        );
    }

    void recordInvitation(User user, long meetingId) {
        meetingsService.invite(meetingId, user.id());
        var userMeeting = UserMeeting.builder()
            .userId(user.id())
            .meetingId(meetingId)
            .userRole(RoleOfUser.INVITED)
            .build();

        userMeetings.remove(userMeeting);
        userMeetings.add(userMeeting);
    }

    void recordAcceptance(UserMeeting userMeeting) {
        var userId = userMeeting.userId();
        var meetingId = userMeeting.meetingId();
        if (meetingsService.accept(meetingId, userId)) {
            var userMeeting1 = UserMeeting.builder()
                .userId(userId)
                .meetingId(meetingId)
                .userRole(RoleOfUser.ACCEPTED)
                .build();
            userMeetings.remove(userMeeting1);
            userMeetings.add(userMeeting1);
        }
    }

    List<User> getAvailableUsers() {

        return users;
    }

    Set<Long> getAllMeetingIds() {
        return idToMeeting.keySet();
    }


    List<Meeting> getAllMeetings() {
        return meetings;
    }

    Set<UserMeeting> findAllUserMeetings() {
        return userMeetings;
    }

    Meeting findMeetingById(long needle) {
        return idToMeeting.get(needle);
    }

    void assertNoUserHasOverlappingMeetings() {
        assertThat(hasOverlap()).isFalse();
    }

    private boolean hasOverlap() {
        Map<Long, Set<Meeting>> userToChronoMeetings = new HashMap<>();
        for (User u : users) {
            userToChronoMeetings.putIfAbsent(u.id(), new TreeSet<>((o1, o2) -> {
                if (o1.id() == o2.id()) {
                    return 0;
                }
                if (o1.startAt().isEqual(o2.startAt())) {
                    return Long.compare(o1.id(), o2.id());
                }
                return o1.startAt().compareTo(o2.startAt());
            }));
        }
        for (UserMeeting userMeeting : userMeetings) {
            if (userMeeting.userRole() == RoleOfUser.OWNER
                || userMeeting.userRole() == RoleOfUser.ACCEPTED) {
                var meeting = idToMeeting.get(userMeeting.meetingId());
                userToChronoMeetings.get(userMeeting.userId()).add(meeting);
            }
        }

        for (long userId : userToChronoMeetings.keySet()) {
            var prevEnd = OpGenTests.LOWER_BOUND_TS.minusYears(10);
            for (var meeting : userToChronoMeetings.get(userId)) {
                if (meeting.startAt().isEqual(prevEnd) || meeting.startAt().isBefore(prevEnd)) {
                    return true;
                }
                prevEnd = prevEnd.isBefore(meeting.endAt()) ? meeting.endAt() : prevEnd;
            }
        }

        return false;
    }
}