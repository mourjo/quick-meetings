package me.mourjo.quickmeetings.it;

import java.time.ZonedDateTime;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.MeetingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public abstract class BaseIT {

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

    MeetingUtils meetingUtils;

    ZonedDateTime now = ZonedDateTime.now();

    User alice, bob, charlie, dick, erin, frank;

    @AfterEach
    void teardown() {
        userMeetingRepository.deleteAll();
        userRepository.deleteAll();
        meetingRepository.deleteAll();
    }


    @BeforeEach
    void setup() {
        meetingUtils = new MeetingUtils(
            userService, meetingsService, meetingRepository, userRepository, userMeetingRepository
        );

        alice = userService.createUser("Alice");
        bob = userService.createUser("Bob");
        charlie = userService.createUser("Charlie");
        dick = userService.createUser("Dick");
        erin = userService.createUser("Erin");
        frank = userService.createUser("Frank");
    }

}
