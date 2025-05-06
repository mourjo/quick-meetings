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
import java.util.stream.Collectors;
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
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.Tuple;
import net.jqwik.api.Tuple.Tuple4;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.internal.properties.arbitraries.DefaultOffsetDateTimeArbitrary;
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
    public static final OffsetDateTime UPPER_BOUND_TS = LOWER_BOUND_TS.plusHours(2);
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
    @Autowired
    JdbcTemplate jdbcTemplate;

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
        @Autowired UserMeetingRepository userMeetingRepository) {
        userMeetingRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();
        alice = userService.createUser("alice");
        bob = userService.createUser("bob");
        charlie = userService.createUser("charlie");

    }

    // (shrinking = ShrinkingMode.FULL)
    @Property(shrinking = ShrinkingMode.FULL)
    void invariant(@ForAll("meetingActions") ActionChain<MeetingState> chain) {
        chain.withInvariant(state -> {
            state.refresh();

            state.findAllUserMeetings()
                .parallelStream()
                .filter(userMeeting -> userMeeting.userRole() == RoleOfUser.OWNER
                    || userMeeting.userRole() == RoleOfUser.ACCEPTED)
                .forEach(userMeeting -> {

                    var meeting = state.findMeetingById(userMeeting.meetingId());

                    assertThat(
                        state.overlappingMeetingForUser(
                            userMeeting.userId(),
                            meeting.startAt(),
                            meeting.endAt()
                        ).stream().map(m -> m.id()).collect(Collectors.toSet()).size()
                    ).isEqualTo(1);
                });
        }).run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {
        return ActionChain.startWith(this::init)
            .withAction(new CreateMeetingAction(LOWER_BOUND_TS, UPPER_BOUND_TS))
//            .withAction(new AcceptInvitationAction())
            .withAction(new CreateInvitationAction());
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
        previousState.refresh();
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

    OffsetDateTime minTime;
    OffsetDateTime maxTime;

    public CreateMeetingAction(OffsetDateTime minTime, OffsetDateTime maxTime) {
        this.minTime = minTime;
        this.maxTime = maxTime;
    }

    Arbitrary<Tuple4<String, User, OffsetDateTime, Integer>> meetingInputs(
        List<User> availableUsers) {
        Arbitrary<OffsetDateTime> starts = new DefaultOffsetDateTimeArbitrary()
            .atTheEarliest(minTime.toLocalDateTime())
            .atTheLatest(maxTime.toLocalDateTime());

        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(5),
            Arbitraries.of(availableUsers),
            starts,
            Arbitraries.integers().between(30, 60)
        ).as(Tuple::of);
    }

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer(MeetingState previousState) {
        return meetingInputs(previousState.getAvailableUsers())
            .map(tuple -> Transformer.mutate(
                    String.format("user-%s-%s is creating a meeting from [%s] to [%s]",
                        tuple.get2().id(), tuple.get2().name(),
                        tuple.get3(), tuple.get3().plusMinutes(tuple.get4())),
                    state -> {
                        try {
                            var name = tuple.get1();
                            var user = tuple.get2();
                            var from = tuple.get3();
                            var durationMins = tuple.get4();
                            var to = from.plusMinutes(durationMins);
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
        userMeetings.add(UserMeeting.builder()
            .userId(user.id())
            .meetingId(meetingId)
            .userRole(RoleOfUser.INVITED)
            .build()
        );
    }

    void recordAcceptance(UserMeeting userMeeting) {
        var userId = userMeeting.userId();
        var meetingId = userMeeting.meetingId();
        meetingsService.accept(meetingId, userId);
        var user = users.stream().filter(u -> u.id() == userId).findFirst().get();

        userMeetings.add(UserMeeting.builder()
            .userId(user.id())
            .meetingId(meetingId)
            .userRole(RoleOfUser.ACCEPTED)
            .build()
        );
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

    List<Meeting> overlappingMeetingForUser(long userId, OffsetDateTime from, OffsetDateTime to) {
        Set<Long> engagements = findAllUserMeetings().stream().filter(
            um -> um.userId() == userId
                && (um.userRole() == RoleOfUser.OWNER || um.userRole() == RoleOfUser.ACCEPTED)
        ).map(UserMeeting::meetingId).collect(Collectors.toSet());

        return getAllMeetings().stream().filter(meeting -> engagements.contains(meeting.id())
            && (
            (meeting.startAt().isAfter(from) || meeting.startAt().equals(from)) &&
                (meeting.endAt().isBefore(to) || meeting.endAt().equals(to))
        )).toList();

    }


    void refresh() {

    }

}