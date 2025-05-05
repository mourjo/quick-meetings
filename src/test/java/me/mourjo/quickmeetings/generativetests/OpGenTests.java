package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
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
    public static final OffsetDateTime UPPER_BOUND_TS = LOWER_BOUND_TS.plusHours(24);
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
        userMeetingRepository.deleteAll();
        meetingRepository.deleteAll();
    }

    public MeetingState init() {
        return new MeetingState(
            meetingsService,
            meetingRepository,
            userService,
            userRepository,
            userMeetingRepository
        );

    }

    @BeforeProperty
    void createUsers(@Autowired UserService userService1) {
        alice = userService1.createUser("alice");
        bob = userService1.createUser("bob");
        charlie = userService1.createUser("charlie");
    }

    @Property
    void checkMyStack(@ForAll("meetingActions") ActionChain<MeetingState> chain) {
        chain.run();

        var finalState = chain.finalState().get();

//        for (var meetingId : finalState.userToMeetings.get(alice)) {
//            var meeting = meetingRepository.findById(meetingId).get();
//            var overlapping = meetingRepository.findOverlappingMeetingsForUser(alice.id(),
//                meeting.startAt(), meeting.endAt());
//            assertThat(overlapping.size()).isEqualTo(1)
//                .withFailMessage(() -> "failed for" + meeting);
//        }

    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {
        return ActionChain.startWith(this::init)
            .withAction(new CreateMeetingAction(List.of(alice, bob, charlie), LOWER_BOUND_TS,
                UPPER_BOUND_TS))
            .withAction(new CheckOverlappingAction(List.of(alice, bob, charlie), LOWER_BOUND_TS,
                UPPER_BOUND_TS));
    }

}

class CheckOverlappingAction implements Action.Independent<MeetingState> {

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
    public Arbitrary<Transformer<MeetingState>> transformer() {

        return new DefaultOffsetDateTimeArbitrary()
            .atTheEarliest(minTime.toLocalDateTime())
            .atTheLatest(maxTime.toLocalDateTime())
            .map(element -> Transformer.mutate(
                    "verifying at " + element,
                    state -> assertThat(
                        state.meetingRepository.findOverlappingMeetingsForUser(
                            availableUsers.get(0).id(),
                            element, element
                        ).size()
                    ).isLessThanOrEqualTo(1)
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
            Arbitraries.integers().between(1, 60)
        ).as(Tuple::of);
    }

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer() {

        return meetingInputs()
            .map(element -> Transformer.mutate(
                    String.format("creating a meeting (%s)", element),
                    state -> {
                        try {
                            var name = element.get1();
                            var user = element.get2();
                            var from = element.get3();
                            var durationMins = element.get4();
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