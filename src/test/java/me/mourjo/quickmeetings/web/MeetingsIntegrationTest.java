package me.mourjo.quickmeetings.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
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
        var user = userService.createUser("justin");
        String meetingName = "J bang %s".formatted(UUID.randomUUID());
        var req = MockMvcRequestBuilders.post("/meeting")
            .content("""
                {
                  "userId": %s,
                  "name": "%s",
                  "duration": {
                    "from": {
                      "date": "2025-03-30",
                      "time": "02:30:00"
                    },
                    "to": {
                      "date": "2025-03-30",
                      "time": "03:00:00"
                    }
                  },
                  "timezone": "Asia/Kolkata"
                }
                """.formatted(user.id(), meetingName))
            .contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(req)
            .andExpect(status().is2xxSuccessful());

        boolean found = false;
        for (var meeting : meetingRepository.findAll()) {
            if (meeting.name().equals(meetingName)) {
                found = true;
                var it = userMeetingRepository.findAll().iterator();
                var meetings = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false
                    ).filter(m -> meeting.id() == m.meetingId())
                    .toList();

                assertThat(meetings.size()).isEqualTo(1);
                assertThat(meetings.get(0).userId()).isEqualTo(user.id());
            }
        }

        assertThat(found).isTrue();

    }


}