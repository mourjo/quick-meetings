package me.mourjo.quickmeetings;

import java.time.OffsetDateTime;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QuickmeetingsApplicationTests {

    @Autowired
    MeetingRepository repository;

    @Test
    void contextLoads() {
        var meeting = Meeting.builder()
            .startAt(OffsetDateTime.now())
            .endAt(OffsetDateTime.now().plusHours(1))
            .name("Test meeting")
            .build();
        var m = repository.save(meeting);

        boolean found = false;
        for (var dbMeeting : repository.findAll()) {
            if (dbMeeting.id() == m.id()) {
                found = true;

                Assertions.assertEquals(meeting.name(), dbMeeting.name());
                Assertions.assertEquals(meeting.startAt().toInstant(),
                    dbMeeting.startAt().toInstant());
                Assertions.assertEquals(meeting.endAt().toInstant(), dbMeeting.endAt().toInstant());

                Assertions.assertTrue(OffsetDateTime.now().isAfter(dbMeeting.createdAt()));
                Assertions.assertTrue(OffsetDateTime.now().isAfter(dbMeeting.updatedAt()));
            }
        }

        Assertions.assertTrue(found);

        var dbMeeting = repository.findById(m.id()).get();
        repository.save(Meeting.buildFrom(dbMeeting).name("New Name").build());

        Assertions.assertEquals("New Name", repository.findById(m.id()).get().name());


    }

}
