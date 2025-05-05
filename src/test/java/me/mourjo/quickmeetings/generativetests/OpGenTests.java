package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
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

        // todo remove this initial seed - test for invitations separately
        meetingsService.createMeeting(
            "Pre-test",
            bob.id(),
            LOWER_BOUND_TS.plusMinutes(10),
            LOWER_BOUND_TS.plusMinutes(15)
        );
        return new MeetingState(
            meetingsService,
            meetingRepository,
            userService,
            userRepository,
            userMeetingRepository
        );

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
    void checkMyStack(@ForAll("meetingActions") ActionChain<MeetingState> chain) {
        chain.withInvariant(state -> {

            state.userMeetingRepository.findAll()
                .parallelStream()
                .filter(userMeeting -> userMeeting.userRole() == RoleOfUser.OWNER)
                .forEach(userMeeting -> {

                    var meeting = state.meetingRepository.findById(userMeeting.meetingId()).get();

                    var userId = userMeeting.userId();
                    assertThat(
                        state.meetingRepository.findOverlappingMeetingsForUser(
                            userId,
                            meeting.startAt(),
                            meeting.endAt()
                        ).size()
                    ).isLessThanOrEqualTo(1);

                });

        }).run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {
        return ActionChain.startWith(this::init)
            .withAction(new CreateMeetingAction(List.of(alice, bob, charlie), LOWER_BOUND_TS,
                UPPER_BOUND_TS))
//            .withAction(new CheckOverlappingAction(List.of(alice, bob, charlie), LOWER_BOUND_TS,
//                UPPER_BOUND_TS))
//            .withAction(new AcceptInvitationAction())
            .withAction(new CreateInvitationAction())

            ;
    }

}

class CheckOverlappingAction implements Action.Dependent<MeetingState> {

    List<User> availableUsers;
    OffsetDateTime minTime;
    OffsetDateTime maxTime;

    public CheckOverlappingAction(List<User> availableUsers, OffsetDateTime minTime,
        OffsetDateTime maxTime) {
        this.availableUsers = availableUsers;
        this.minTime = minTime;
        this.maxTime = maxTime;
    }

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer(MeetingState previousState) {
        return Arbitraries.of(previousState.meetingRepository.findAll())
            .map(meeting -> Transformer.mutate(
                    "Verifying meeting " + meeting,
                    state -> {
                        var userMeetings = state.userMeetingRepository.findAllByMeetingId(meeting.id());
                        for (var userMeeting : userMeetings) {
                            var userId = userMeeting.userId();
                            assertThat(
                                state.meetingRepository.findOverlappingMeetingsForUser(
                                    userId,
                                    meeting.startAt(),
                                    meeting.endAt()
                                ).size()
                            ).isLessThanOrEqualTo(1);
                        }

                    }
                )
            );
    }
}


class CreateInvitationAction implements Action.Dependent<MeetingState> {

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer(MeetingState previousState) {
        Arbitrary<Long> meetingIds = Arbitraries.of(
            previousState.meetingRepository.findAll().stream().map(Meeting::id).toList());
        Arbitrary<User> users = Arbitraries.of(
            previousState.userRepository.findAll()
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
                        } catch (MeetingNotFoundException ex) {
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
        var invitations = Arbitraries.of(previousState.userMeetingRepository.findAll());

        return invitations
            .map(invitation -> Transformer.mutate(
                    "User-%s is accepting meeting-%s".formatted(
                        invitation.userId(),
                        invitation.meetingId()),
                    state -> {
                        try {
                            state.meetingsService.accept(invitation.meetingId(),
                                invitation.userId());
                        } catch (MeetingNotFoundException ex) {
                            // ignored
                        }
                    }
                )
            );
    }
}


class CreateMeetingAction implements Action.Independent<MeetingState> {

    List<User> availableUsers;
    OffsetDateTime minTime;
    OffsetDateTime maxTime;

    public CreateMeetingAction(List<User> users, OffsetDateTime minTime, OffsetDateTime maxTime) {
        this.availableUsers = users;
        this.minTime = minTime;
        this.maxTime = maxTime;
    }

    Arbitrary<Tuple4<String, User, OffsetDateTime, Integer>> meetingInputs() {
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
    public Arbitrary<Transformer<MeetingState>> transformer() {
        return meetingInputs()
            .map(tuple -> Transformer.mutate(
                    String.format("creating a meeting (%s)", tuple),
                    state -> {
                        try {
                            var name = tuple.get1();
                            var user = tuple.get2();
                            var from = tuple.get3();
                            var durationMins = tuple.get4();
                            var to = from.plusMinutes(durationMins);

                            long meetingId = state.meetingsService.createMeeting(
                                name,
                                user.id(),
                                from,
                                to
                            );
                            assertThat(meetingId).isGreaterThan(0);

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

    public MeetingState(MeetingsService meetingsService, MeetingRepository meetingRepository,
        UserService userService, UserRepository userRepository,
        UserMeetingRepository userMeetingRepository) {
        this.meetingsService = meetingsService;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.userMeetingRepository = userMeetingRepository;
        this.userService = userService;
    }

}