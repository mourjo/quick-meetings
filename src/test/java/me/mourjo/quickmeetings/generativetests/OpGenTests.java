package me.mourjo.quickmeetings.generativetests;

import static me.mourjo.quickmeetings.generativetests.OpGenTests.LOWER_BOUND_TS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeeting;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.Tuple.Tuple4;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.Action.JustMutate;
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
    UserMeetingRepository userMeetingRepository;
    @Autowired
    MeetingRepository meetingRepository;
    @Autowired
    MeetingsService meetingsService;
    List<User> users;

    public MeetingState init() {
        var state = new MeetingState(
            meetingsService,
            userMeetingRepository,
            meetingRepository,
            users
        );

        // todo remove this initial seed - test for invitations separately

        return state;

    }

    @BeforeProperty
    void createUsers(@Autowired UserService userService, @Autowired UserRepository userRepository,
        @Autowired MeetingRepository meetingRepository,
        @Autowired UserMeetingRepository userMeetingRepository,
        @Autowired JdbcTemplate jdbcTemplate) {
        userMeetingRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();
        User alice = userService.createUser("alice");
        User bob = userService.createUser("bob");
        User charlie = userService.createUser("charlie");
        users = List.of(alice, bob, charlie);
    }

    // (shrinking = ShrinkingMode.FULL, afterFailure = AfterFailureMode.RANDOM_SEED)
    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void invariant(@ForAll("meetingActions") ActionChain<MeetingState> chain) {

        chain.withInvariant(MeetingState::assertNoUserHasOverlappingMeetings).run();
    }

    @Provide
    Arbitrary<ActionChain<MeetingState>> meetingActions() {
        return ActionChain.startWith(this::init)
            .withAction(new CreateMeetingAction())
            .withAction(new AcceptInvitationAction())
            .withAction(new CreateInvitationAction())
            ;
    }
}


class CreateInvitationAction extends JustMutate<MeetingState> {

    Random r = new Random();
    String descrip = "CreateInvitationAction";

    @Override
    public boolean precondition(MeetingState state) {
        return state.getMeetingCount() > 0;
    }

    @Override
    public void mutate(MeetingState state) {
        var meetingIds = new ArrayList<>(state.getAllMeetingIds());

        var users = state.getAvailableUsers();
        var user = users.get(r.nextInt(0, users.size()));
        //var meetings = state.findLongestMeetings();

        var meetingId = meetingIds.get(r.nextInt(0, meetingIds.size()));
        descrip = "user-%s is invited to meeting-%s".formatted(user.id(), meetingId);
        state.recordInvitation(
            user,
            meetingId
        );
//
//
//        for (User user : users) {
//            for (Meeting meeting : meetings) {
//                if (state.recordInvitation(user, meeting.id())) {
//                    descrip = "user-%s is invited to meeting-%s".formatted(user.id(), meeting.id());
//                    break;
//                }
//            }
//        }
    }

    @Override
    public String description() {
        return descrip;
    }
}

class AcceptInvitationAction extends JustMutate<MeetingState> {

    Random r = new Random();
    String descrp = "AcceptInvitation";

    @Override
    public void mutate(MeetingState state) {
        var userMeetings = new ArrayList<>(state.findAllUserMeetings());
        var um = userMeetings.get(r.nextInt(0, userMeetings.size()));
        descrp = "user-%s is accepting meeting-%s".formatted(um.userId(), um.meetingId());
        state.recordAcceptance(um);

//        for (User user : state.getAvailableUsers()) {
//            for (Meeting meeting : state.findLongestMeetings()) {
//                var um = UserMeeting.builder()
//                    .userId(user.id())
//                    .meetingId(meeting.id())
//                    .build();
//                if (state.recordAcceptance(um)) {
//                    break;
//                }
//
//            }
//        }
    }

    @Override
    public String description() {
        return descrp;
    }

    @Override
    public boolean precondition(MeetingState state) {
        return state.getMeetingCount() > 0;
    }
}

class CreateMeetingAction implements Action.Independent<MeetingState> {


    int maxOffsetMins, maxDurationMins;

    public CreateMeetingAction() {
        this.maxDurationMins = 60;
        this.maxOffsetMins = 60;
    }

    Arbitrary<Tuple4<String, Integer, Integer, Integer>> meetingInputs(
        int countOfUsers) {
        var durationMins = Arbitraries.integers().between(1, maxDurationMins);
        var startOffsetMins = Arbitraries.integers().between(1, maxOffsetMins);

        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(5),
            Arbitraries.integers().between(0, countOfUsers - 1),
            startOffsetMins,
            durationMins
        ).as(Tuple::of);
    }

    @Override
    public Arbitrary<Transformer<MeetingState>> transformer() {
        return meetingInputs(3)
            .map(tuple -> Transformer.mutate(
                    String.format("user-%s is creating a meeting from [%s] to [%s]",
                        tuple.get2(),
                        LOWER_BOUND_TS.plusMinutes(tuple.get3()),
                        LOWER_BOUND_TS.plusMinutes(tuple.get3() + tuple.get4())),
                    state -> {
                        var name = tuple.get1();
                        var userIdx = tuple.get2();
                        var from = LOWER_BOUND_TS.plusMinutes(tuple.get3());
                        var to = LOWER_BOUND_TS.plusMinutes(tuple.get3() + tuple.get4());
                        state.recordCreation(name, userIdx, from, to);
                    }
                )
            );
    }
}

class MeetingState {

    private final MeetingsService meetingsService;
    private final List<User> users;
    private final List<Meeting> meetings;
    private final Map<Long, Meeting> idToMeeting;

    private final Set<UserMeeting> userMeetings;
    private final UserMeetingRepository userMeetingRepository;
    private final MeetingRepository meetingRepository;

    public MeetingState(MeetingsService meetingsService,
        UserMeetingRepository userMeetingRepository, MeetingRepository meetingRepository,
        List<User> users) {

        this.userMeetingRepository = userMeetingRepository;
        this.meetingRepository = meetingRepository;
        this.meetingsService = meetingsService;
        this.users = users;

        meetings = new ArrayList<>();
        idToMeeting = new HashMap<>();

        userMeetings = new TreeSet<>((o1, o2) -> {
            if (o1.meetingId() == o2.meetingId()) {
                return Long.compare(o1.userId(), o2.userId());
            }
            return Long.compare(o1.meetingId(), o2.meetingId());
        });

        this.meetingRepository.deleteAll();
        this.userMeetingRepository.deleteAll();
    }

    UserMeeting idxToUserMeeting(int idx) {
        for (var userMeeting : userMeetings) {
            if (idx-- == 0) {
                return userMeeting;
            }
        }
        return null;
    }

    void recordCreation(String name, int userIdx, OffsetDateTime from, OffsetDateTime to) {
        try {
            var meeting = meetingsService.createMeeting(
                name,
                users.get(userIdx).id(),
                from,
                to
            );

            meetings.add(meeting);
            idToMeeting.put(meeting.id(), meeting);

            userMeetings.add(UserMeeting.builder()
                .userId(users.get(userIdx).id())
                .meetingId(meeting.id())
                .userRole(RoleOfUser.OWNER)
                .build()
            );
        } catch (OverlappingMeetingsException ignored) {

        }
    }

    boolean recordInvitation(User user, long meetingId) {
        try {
            var invitees = meetingsService.invite(meetingId, user.id());
            if (!invitees.isEmpty()) {
                var userMeeting = UserMeeting.builder()
                    .userId(user.id())
                    .meetingId(meetingId)
                    .userRole(RoleOfUser.INVITED)
                    .build();

                userMeetings.remove(userMeeting);
                userMeetings.add(userMeeting);
                return true;
            }
        } catch (OverlappingMeetingsException ignored) {

        }
        return false;
    }

    boolean recordAcceptance(UserMeeting userMeeting) {
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
            return true;
        }
        return false;
    }

    List<User> getAvailableUsers() {
        return users;
    }

    Set<Long> getAllMeetingIds() {
        return idToMeeting.keySet();
    }

    Set<UserMeeting> findAllUserMeetings() {
        return userMeetings;
    }

    int getMeetingCount() {
        return meetings.size();
    }

    void assertNoUserHasOverlappingMeetings() {
        assertThat(hasOverlap()).isFalse();
    }

    List<Meeting> findLongestMeetings() {
        meetings.sort(new Comparator<Meeting>() {
            @Override
            public int compare(Meeting o1, Meeting o2) {
                var duration1 = Duration.between(o1.startAt(), o1.endAt());
                var duration2 = Duration.between(o2.startAt(), o2.endAt());
                return duration2.compareTo(duration1);
            }
        });
        return meetings;
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
            var prevEnd = LOWER_BOUND_TS.minusYears(10);
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