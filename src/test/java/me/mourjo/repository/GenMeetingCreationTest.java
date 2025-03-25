package me.mourjo.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.arbitraries.ArbitraryDecorator;
import net.jqwik.api.arbitraries.ListArbitrary;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.time.internal.properties.arbitraries.DefaultOffsetDateTimeArbitrary;
import org.junit.jupiter.api.Assertions;

@Slf4j
public class GenMeetingCreationTest {

    final static MeetingRepository repo = new MeetingRepository();
    final static OffsetDateTime now = OffsetDateTime.now();
    static long id = -1;

    @BeforeTry
    public void setup() {
        repo.deleteAll();
    }

    @AfterTry
    public void teardown() {
        repo.deleteAll();
    }

    @Property
    void uniqueInList(@ForAll("bounds") @Size(max=5) List<StartEnd> startEnds) {
        for (var startEnd : startEnds) {
            if (!repo.exists(startEnd.start, startEnd.end)) {
                repo.insert(
                    UUID.randomUUID().toString(),
                    startEnd.start,
                    startEnd.end
                );
            }
        }

        for (var startEnd : startEnds) {
            var overlaps = repo.meetingsInRange(startEnd.start);
            var numOverlaps = overlaps.size();
            Assertions.assertTrue(numOverlaps == 0 || numOverlaps == 1,
                "Found %s overlaps in %s\n%s and %s".formatted(
                    numOverlaps,
                    startEnd,
                    overlaps.size() <= 0 ? null : new StartEnd(overlaps.get(0).getStartTime(), overlaps.get(0).getEndTime()),
                    overlaps.size() <= 1 ? null : new StartEnd(overlaps.get(1).getStartTime(), overlaps.get(1).getEndTime())
                ));
        }

        for (var startEnd : startEnds) {
            var overlaps = repo.meetingsInRange(startEnd.end);
            var numOverlaps = overlaps.size();
            Assertions.assertTrue(numOverlaps == 0 || numOverlaps == 1,
                "Found %s overlaps in %s\n%s and %s".formatted(
                    numOverlaps,
                    startEnd,
                    overlaps.size() <= 0 ? null : new StartEnd(overlaps.get(0).getStartTime(), overlaps.get(0).getEndTime()),
                    overlaps.size() <= 1 ? null : new StartEnd(overlaps.get(1).getStartTime(), overlaps.get(1).getEndTime())
                ));
        }
    }

    @Provide
    ListArbitrary<StartEnd> bounds() {
        return new StartEndArbitrary().list();
    }


    class StartEnd {

        @Override
        public String toString() {
            return "StartEnd{" +
                "start=" + start.toLocalDateTime() +
                ", end=" + end.toLocalDateTime() +
                '}';
        }

        final OffsetDateTime start;
        final OffsetDateTime end;

        StartEnd(OffsetDateTime start, OffsetDateTime end) {
            if (start.isAfter(end)) {
                this.end = start;
                this.start = end;
            } else {
                this.start = start;
                this.end = end.plusMinutes(1);
            }
        }
    }

    class StartEndArbitrary extends ArbitraryDecorator<StartEnd> {


        @Override
        protected Arbitrary<StartEnd> arbitrary() {
            var start = new DefaultOffsetDateTimeArbitrary();
            var end = new DefaultOffsetDateTimeArbitrary();

            return Combinators.combine(start, end).as(StartEnd::new);
        }
    }

}
