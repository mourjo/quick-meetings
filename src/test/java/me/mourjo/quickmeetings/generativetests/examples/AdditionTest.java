package me.mourjo.quickmeetings.generativetests.examples;


import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.AfterFailureMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

@net.jqwik.api.Disabled
@org.junit.jupiter.api.Disabled
public class AdditionTest {

    public static double add(double x, double y) {
        return x + y;
    }

    @Test
    void exampleBasedAdditionTest() {
        assertEquals(1.5, add(1.5, 0));
        assertEquals(2.1, add(1.1, 1));
        assertEquals(5.2, add(2.1, 3.1));
        assertEquals(0, add(-1, 1));
        assertEquals(-101.8, add(-100, -1.8));
        assertEquals(-101, add(-1, -100));
        assertEquals(230.5, add(130.5, 100));
        assertEquals(231, add(100.6, 130.4));
    }

    @Property(afterFailure = AfterFailureMode.RANDOM_SEED)
    void propertyBasedAdditionTest(@ForAll double a, @ForAll double b) {
        assertEquals(a, add(a, 0), "Additive Identity");
        assertEquals(0, add(a, -a), "Additive Inverse");
        assertEquals(add(a, b), add(b, a), "Commutativity");
        assertEquals(add(1, add(a, b)), add(add(1, a), b), "Associativity");
    }

}
