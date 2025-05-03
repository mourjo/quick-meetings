package me.mourjo.quickmeetings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MeetingsIntegrationTest {

    @Autowired
    MeetingRepository repository;

    @Test
    void contextLoads() {
        var meeting = Meeting.builder()
            .startAt(OffsetDateTime.now())
            .endAt(OffsetDateTime.now().plusHours(1))
            .name("Test meeting")
            .build();
        var savedMeeting = repository.save(meeting);

        boolean found = false;
        for (var dbMeeting : repository.findAll()) {
            if (dbMeeting.id() == savedMeeting.id()) {
                found = true;
                assertThat(dbMeeting.name()).isEqualTo(meeting.name());

                assertThat(dbMeeting.startAt().toEpochSecond())
                    .isEqualTo(meeting.startAt().toEpochSecond());

                assertThat(dbMeeting.endAt().toEpochSecond())
                    .isEqualTo(meeting.endAt().toEpochSecond());

                Assertions.assertTrue(OffsetDateTime.now().isAfter(dbMeeting.createdAt()));
                Assertions.assertTrue(OffsetDateTime.now().isAfter(dbMeeting.updatedAt()));
            }
        }

        assertThat(found).isTrue();

        var dbMeeting = repository.findById(savedMeeting.id()).get();
        repository.save(Meeting.buildFrom(dbMeeting).name("New Name").build());

        assertThat(
            repository.findById(savedMeeting.id()).get().name()
        ).isEqualTo("New Name");
    }

}
