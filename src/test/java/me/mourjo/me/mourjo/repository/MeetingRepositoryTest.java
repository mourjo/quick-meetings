package me.mourjo.me.mourjo.repository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import me.mourjo.entities.Meeting;
import me.mourjo.repository.MeetingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MeetingRepositoryTest {

    @Test
    void insert() {
        var repo = new MeetingRepository();
        long id = -1;
        try {
            var name = "A new Meeting - " + System.currentTimeMillis();
            var rowsInserted = repo.insert(
                name,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(1)
            );

            Assertions.assertEquals(1, rowsInserted);

            Assertions.assertTrue(!repo.fetchAll().isEmpty());

            Optional<Meeting> insertedMeeting = repo.fetchAll().stream()
                .filter(row -> name.equals(row.getName()))
                .findFirst();

            Assertions.assertTrue(insertedMeeting.isPresent());
            id = insertedMeeting.get().getId();

        } finally {
            if (id != -1) {
                repo.delete(id);
            }
        }


    }
}
