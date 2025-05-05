package me.mourjo.quickmeetings.generativetests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
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
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;
import net.jqwik.spring.JqwikSpringSupport;
import net.jqwik.time.internal.properties.arbitraries.DefaultLocalDateTimeArbitrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@JqwikSpringSupport
@SpringBootTest
public class OpGenTests {

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
    void cleanup() {
        userMeetingRepository.deleteAll();
        meetingRepository.deleteAll();
    }


    public MeetingState init() {

        return new MeetingState(
            meetingsService,
            meetingRepository,
            userRepository,
            userMeetingRepository,
            userService,
            alice,
            bob,
            charlie
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

//        if (alice == null) {
//            alice = userService.createUser("alice");
//            bob = userService.createUser("bob");
//            charlie = userService.createUser("charlie");
//        }
        chain.run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {
        return ActionChain.startWith(this::init)
            .withAction(new CreateMeetingAction());
    }

}

class CreateMeetingAction implements Action.Independent<MeetingState> {

    static final LocalDateTime now = LocalDateTime.of(
        2025,
        6,
        9,
        10,
        20,
        0,
        0
    );

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer() {
        Arbitrary<String> meetingNames = Arbitraries.strings().alpha().ofLength(5);
        //Arbitrary<User> users = Arbitraries.of(List.of(alice, bob, charlie));

        Arbitrary<LocalDateTime> starts = new DefaultLocalDateTimeArbitrary().atTheEarliest(now)
            .atTheLatest(now.plusHours(24));
        Arbitrary<Integer> meetingLengthMins = Arbitraries.integers().between(1, 60);

        Arbitrary<MeetingCreationArgs> arb = Combinators.combine(
            meetingNames,
            starts,
            meetingLengthMins
        ).as((n, s, sl) -> {

            var zonedDt = ZonedDateTime.of(s, ZoneOffset.UTC);

            return MeetingCreationArgs.builder()
                .name(n)
                .from(zonedDt)
                .to(zonedDt.plusMinutes(sl))
                .build();

        });

        return arb.map(element -> Transformer.mutate(
            String.format("creating a meeting (%s)", element),
            meetingState -> {
                try {
                    long meetingId = meetingState.meetingsService.createMeeting(
                        element.name(),
                        meetingState.alice.id(),
                        element.from(),
                        element.to()
                    );
                    assertThat(meetingId).isGreaterThan(0);
                    meetingState.userToMeetings.putIfAbsent(meetingState.alice, new ArrayList<>());
                    meetingState.userToMeetings.get(meetingState.alice).add(meetingId);
                } catch (OverlappingMeetingsException ex) {
                    // ignore
                }
            }
        ));
    }
}

@Builder
record MeetingCreationArgs(String name, ZonedDateTime from, ZonedDateTime to) {

}


class MeetingState {

    public Map<User, List<Long>> userToMeetings;

    public MeetingsService meetingsService;
    public MeetingRepository meetingRepository;
    public UserRepository userRepository;
    public UserMeetingRepository userMeetingRepository;
    public UserService userService;
    public User alice, bob, charlie;

    public MeetingState(MeetingsService meetingsService, MeetingRepository meetingRepository,
        UserRepository userRepository, UserMeetingRepository userMeetingRepository,
        UserService userService, User alice, User bob, User charlie) {
        this.meetingsService = meetingsService;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.userMeetingRepository = userMeetingRepository;
        this.userService = userService;
        this.alice = alice;
        this.bob = bob;
        this.charlie = charlie;
        this.userToMeetings = new HashMap<>();
        userToMeetings.put(alice, new ArrayList<>());
        userToMeetings.put(bob, new ArrayList<>());
        userToMeetings.put(charlie, new ArrayList<>());
    }
}