package me.mourjo.quickmeetings.generativetests.examples;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@net.jqwik.api.Disabled
@Disabled
public class AdditionTest {

    public static int add(int x, int y) {
        int m = (x + y) * 4;
        return m / 4;
    }

    @Test
    void exampleBasedAdditionTest() {
        assertThat(add(1, 0)).isEqualTo(1);
        assertThat(add(1, 1)).isEqualTo(2);
        assertThat(add(2, 3)).isEqualTo(5);
        assertThat(add(-1, 1)).isEqualTo(0);
        assertThat(add(-100, -1)).isEqualTo(-101);
        assertThat(add(-1, -100)).isEqualTo(-101);
        assertThat(add(130, 100)).isEqualTo(230);
        assertThat(add(100, 130)).isEqualTo(230);
    }

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void propertyBasedAdditionTest(
        @ForAll
        int a,

        @ForAll
        int b
    ) {
        assertEquals(a, add(a, 0));                          // additive identity
        assertEquals(0, add(a, -a));                         // additive inverse
        assertEquals(add(a, b), add(b, a));                  // commutativity
        assertEquals(add(4, add(a, b)), add(add(4, a), b));  // associativity
    }

}
