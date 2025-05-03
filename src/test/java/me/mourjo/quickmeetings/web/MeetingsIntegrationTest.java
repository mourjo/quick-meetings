package me.mourjo.quickmeetings.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.utils.RequestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class MeetingsIntegrationTest {

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

    @Autowired
    MockMvc mockMvc;

    @SneakyThrows
    @Test
    void createMeetingTest() {
        var startingLocalTime = LocalDateTime.of(2025, 3, 30, 14, 30, 0, 0);
        var zone = "Asia/Kolkata";
        var offset = ZoneId.of(zone).getRules().getOffset(startingLocalTime);
        var startingZonedTime = OffsetDateTime.of(startingLocalTime, offset);

        var user = userService.createUser("justin");
        String meetingName = "Testing strategy meeting %s".formatted(UUID.randomUUID());
        var req = RequestUtils.meetingRequest(
            user.id(),
            meetingName,
            startingLocalTime,
            startingLocalTime.plusMinutes(30),
            zone
        );

        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        var meetingId = Long.parseLong(
            JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString());

        var dbMeeting = meetingRepository.findById(meetingId).get();
        var userMeetings = userMeetingRepository.findAllByMeetingId(dbMeeting.id());

        assertThat(userMeetings.size()).isEqualTo(1);
        assertThat(userMeetings.get(0).userId()).isEqualTo(user.id());

        assertThat(dbMeeting.startAt()).isEqualTo(startingZonedTime);
        assertThat(dbMeeting.endAt()).isEqualTo(startingZonedTime.plusMinutes(30));

    }


}