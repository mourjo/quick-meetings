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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest
public abstract class BaseIT {

    @Autowired
    public MeetingsService meetingsService;

    @Autowired
    public MeetingRepository meetingRepository;

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public UserMeetingRepository userMeetingRepository;

    @Autowired
    public UserService userService;

    @Autowired
    public MockMvc mockMvc;

    public MeetingUtils meetingUtils;

    public ZonedDateTime now = ZonedDateTime.now();

    public User alice, bob, charlie, dick, erin, frank;

    @AfterEach
    void teardown() {
    }


    @BeforeEach
    void setup() {
        userMeetingRepository.deleteAll();
        userRepository.deleteAll();
        meetingRepository.deleteAll();
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
