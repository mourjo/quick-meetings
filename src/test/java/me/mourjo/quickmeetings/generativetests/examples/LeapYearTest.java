package me.mourjo.quickmeetings.generativetests.examples;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@net.jqwik.api.Disabled
@Disabled
public class LeapYearTest {

    Application app = new Application();

    @Test
    void exampleTests() {
        assertThat(app.lastDayOfFeb(2015)).isEqualTo(28);
        assertThat(app.lastDayOfFeb(2020)).isEqualTo(29);
        assertThat(app.lastDayOfFeb(2021)).isEqualTo(28);
    }

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void propTests(@ForAll @IntRange(min = 1940, max = 3000) int year) {
        int calendarDate = LocalDateTime.of(year, 3, 1, 0, 0, 0).minusDays(1).getDayOfMonth();
        assertThat(app.lastDayOfFeb(year)).isEqualTo(calendarDate);
    }

    static class Application {

        int lastDayOfFeb(int year) {
            return year % 4 == 0 ? 29 : 28;
        }
    }
}
