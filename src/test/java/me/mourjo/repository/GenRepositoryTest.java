package me.mourjo.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import me.mourjo.entities.Meeting;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.junit.jupiter.api.Assertions;

@Slf4j
public class GenRepositoryTest {

    final static MeetingRepository repo = new MeetingRepository();
    final static OffsetDateTime now = OffsetDateTime.now();
    static int id = -1;

    @BeforeContainer
    public static void setup() {
        log.info("Running setup");
        var name = "A new Meeting - " + System.currentTimeMillis();
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
    }

    @AfterContainer
    public static void teardown() {
        if (id != -1) {
            log.info("Running teardown");
            repo.delete(id);
        }
    }

    @Property
    void meetingsStartingWithinAnHourOverlap(
        @ForAll @IntRange(min = 0, max = 60) int startOffset,
        @ForAll @IntRange(min = 60, max = 1000) int endOffset
    ) {
        Assertions.assertTrue(
            repo.exists(now.plusMinutes(startOffset), now.plusMinutes(endOffset))
        );

    }

    @Property
    void meetingsEndingWithinAnHourOverlap(
        @ForAll @IntRange(min = 0, max = 1000) int startOffset,
        @ForAll @IntRange(min = 0, max = 60) int endOffset
    ) {
        Assertions.assertTrue(
            repo.exists(now.minusMinutes(startOffset), now.plusMinutes(endOffset))
        );

    }

    @Property
    void meetingsStartingBeforeAndEndingAfterOverlap(
        @ForAll @IntRange(min = 0, max = 1000) int startOffset,
        @ForAll @IntRange(min = 0, max = 1000) int endOffset
    ) {
        Assertions.assertTrue(
            repo.exists(now.minusMinutes(startOffset), now.plusMinutes(endOffset))
        );

    }

    @Property
    void meetingsStartingAfterAndEndingBeforeOverlap(
        @ForAll @IntRange(min = 1, max = 30) int startOffset,
        @ForAll @IntRange(min = 0, max = 30) int endOffset
    ) {
        Assertions.assertTrue(
            repo.exists(now.plusMinutes(startOffset), now.plusMinutes(endOffset))
        );

    }

    @Property
    void meetingsStartingAfterAndEndingAfterDoNotOverlap(
        @ForAll @IntRange(min = 61, max = 100) int startOffset,
        @ForAll @IntRange(min = 62, max = 100) int endOffset
    ) {
        Assertions.assertFalse(
            repo.exists(now.plusMinutes(startOffset), now.plusMinutes(endOffset))
        );

    }

    @Property
    void meetingsStartingBeforeAndEndingBeforeDoNotOverlap(
        @ForAll @IntRange(min = -100, max = -61) int startOffset,
        @ForAll @IntRange(min = -100, max = -62) int endOffset
    ) {
        Assertions.assertFalse(
            repo.exists(now.plusMinutes(startOffset), now.plusMinutes(endOffset))
        );

    }

}
