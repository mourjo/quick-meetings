package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        // todo remove this initial seed - test for invitations separately
        var preTestMeeting = meetingsService.createMeeting(
            "Pre-test",
            bob.id(),
            LOWER_BOUND_TS.plusMinutes(10),
            LOWER_BOUND_TS.plusMinutes(15)
        );

        var state = new MeetingState(
            meetingsService,
            meetingRepository,
            userService,
            userRepository,
            userMeetingRepository
        );
        state.refresh();

        state.recordCreation(bob, meetingRepository.findById(preTestMeeting.id()).get());

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
    @Property
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
                        ).size()
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
                            state.meetingsService.invite(meetingId, user.id());
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
                            state.meetingsService.accept(invitation.meetingId(),
                                invitation.userId());
                            state.recordAcceptance(invitation.userId(), invitation.meetingId());
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

                            var meeting = state.meetingsService.createMeeting(
                                name,
                                user.id(),
                                from,
                                to
                            );

                            state.recordCreation(user, meeting);
                            assertThat(meeting.id()).isGreaterThan(0);

                        } catch (OverlappingMeetingsException ex) {
                            // ignore
                        }
                    }
                )
            );
    }
}

class MeetingState {

    MeetingsService meetingsService;
    MeetingRepository meetingRepository;
    UserRepository userRepository;
    UserMeetingRepository userMeetingRepository;
    UserService userService;
    Map<User, List<Meeting>> ownersToMeetings;
    Map<User, Set<Long>> invitedToMeetings;
    Map<User, Set<Long>> acceptedToMeetings;
    List<User> users;
    List<UserMeeting> userMeetings;
    List<Meeting> allMeetings;

    public MeetingState(MeetingsService meetingsService, MeetingRepository meetingRepository,
        UserService userService, UserRepository userRepository,
        UserMeetingRepository userMeetingRepository) {
        this.meetingsService = meetingsService;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.userMeetingRepository = userMeetingRepository;
        this.userService = userService;
        ownersToMeetings = new HashMap<>();
        invitedToMeetings = new HashMap<>();
        acceptedToMeetings = new HashMap<>();
    }

    void recordCreation(User user, Meeting meeting) {
        ownersToMeetings.get(user).add(meeting);
    }

    void recordInvitation(User user, long meetingId) {
        invitedToMeetings.get(user).add(meetingId);
    }

    void recordAcceptance(long userId, long meetingId) {
        var user = users.stream().filter(u -> u.id() == userId).findFirst().get();
        invitedToMeetings.get(user).remove(meetingId);
        acceptedToMeetings.get(user).add(meetingId);
    }

    List<User> getAvailableUsers() {

        return users;
    }

    List<Long> getAllMeetingIds() {
        return ownersToMeetings.values().stream()
            .flatMap(meeting -> meeting.stream().map(Meeting::id)).toList();
    }


    List<UserMeeting> findAllUserMeetings() {
        return userMeetings;
    }

    Meeting findMeetingById(long needle) {
        return allMeetings.stream().filter(m -> m.id() == needle).findFirst().get();
    }

    List<Meeting> overlappingMeetingForUser(long userId, OffsetDateTime from, OffsetDateTime to) {
        Set<Long> engagements = userMeetings.stream().filter(
            um -> um.userId() == userId
                && (um.userRole() == RoleOfUser.OWNER || um.userRole() == RoleOfUser.ACCEPTED)
        ).map(m -> m.meetingId()).collect(Collectors.toSet());

        return allMeetings.stream().filter(meeting -> engagements.contains(meeting.id())
            && (
            (meeting.startAt().isAfter(from) || meeting.startAt().equals(from)) &&
                (meeting.endAt().isBefore(to) || meeting.endAt().equals(to))
        )).toList();

    }


    void refresh() {
        if (users == null) {
            users = userRepository.findAll();
            for (User u : users) {
                ownersToMeetings.putIfAbsent(u, new ArrayList<>());
                invitedToMeetings.putIfAbsent(u, new HashSet<>());
                acceptedToMeetings.putIfAbsent(u, new HashSet<>());
            }
        }
        userMeetings = new ArrayList<>();
        userMeetings = userMeetingRepository.findAll();
        allMeetings = new ArrayList<>();
        allMeetings = meetingRepository.findAll();
    }

}