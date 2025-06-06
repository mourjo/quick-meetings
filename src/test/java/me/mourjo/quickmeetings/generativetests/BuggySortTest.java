package me.mourjo.quickmeetings.generativetests;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@net.jqwik.api.Disabled
@Disabled
public class BuggySortTest {

    Application app = new Application();

    @Test
    void exampleTests() {
        assertThat(app.bubbleSort(List.of(10, 20, 30))).isEqualTo(List.of(10, 20, 30));
        assertThat(app.bubbleSort(List.of(5, 2, 6))).isEqualTo(List.of(2, 5, 6));
        assertThat(app.bubbleSort(List.of(100, -200, 101))).isEqualTo(List.of(-200, 100, 101));
        assertThat(app.bubbleSort(List.of(100, 80, 530))).isEqualTo(List.of(80, 100, 530));
    }

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void propTests(@ForAll List<Integer> integers) {
        var sortedIntegers = app.bubbleSort(integers);
        for (int i = 0; i < integers.size() - 1; i++) {
            assertThat(sortedIntegers.get(i)).isLessThanOrEqualTo(sortedIntegers.get(i + 1));
        }
    }

    static class Application {

        private static List<Integer> buggySort(List<Integer> arr) {
            int n = arr.size();

            for (int i = 0; i < n - 1; i++) {
                boolean swapped = false;
                // fix: it should be n - i - 1
                for (int j = 0; j < n - i - 2; j++) {
                    if (arr.get(j) > arr.get(j + 1)) {
                        int temp = arr.get(j);
                        arr.set(j, arr.get(j + 1));
                        arr.set(j + 1, temp);
                        swapped = true;
                    }
                }
                if (!swapped) {
                    break;
                }
            }
            return arr;
        }

        List<Integer> bubbleSort(List<Integer> numbers) {
            var result = new ArrayList<>(numbers);
            buggySort(result);
            return result;
        }
    }
}
