package me.mourjo.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import me.mourjo.entities.Meeting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MeetingRepositoryTest {

    @Test
    void insert() {
        var repo = new MeetingRepository();
        long id = -1;
        try {
            var name = "A new Meeting - " + System.currentTimeMillis();
            var now = OffsetDateTime.now();
            var rowsInserted = repo.insert(
                name,
                now,
                now.plusHours(1)
            );

            Assertions.assertEquals(1, rowsInserted);

            Assertions.assertFalse(repo.fetchAll().isEmpty());

            Optional<Meeting> insertedMeeting = repo.fetchAll().stream()
                .filter(row -> name.equals(row.getName()))
                .findFirst();

            Assertions.assertTrue(insertedMeeting.isPresent());
            id = insertedMeeting.get().getId();

            // exact overlap
            Assertions.assertTrue(
                repo.exists(now, now.plusHours(1))
            );

            // new-start overlaps
            Assertions.assertTrue(
                repo.exists(now.plusMinutes(5), now.plusHours(10))
            );

            // new-end overlaps
            Assertions.assertTrue(
                repo.exists(now.minusHours(5), now.plusMinutes(20))
            );

            // subset
            Assertions.assertTrue(
                repo.exists(now.plusMinutes(5), now.plusMinutes(10))
            );

            // superset
            Assertions.assertTrue(
                repo.exists(now.minusHours(5), now.plusHours(10))
            );

            // no overlap - before
            Assertions.assertFalse(
                repo.exists(now.minusHours(10), now.minusHours(9))
            );

            // no overlap - after
            Assertions.assertFalse(
                repo.exists(now.plusHours(9), now.plusHours(10))
            );


        } finally {
            if (id != -1) {
                repo.delete(id);
            }
        }


    }
}
