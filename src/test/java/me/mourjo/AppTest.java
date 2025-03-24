package me.mourjo;


import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AppTest {

    @Test
    public void testMe() {
        Assertions.assertEquals("Hello, world!", App.saySomething());
    }

    @Property
    void lengthOfConcatenatedStringIsGreaterThanLengthOfEach(
        @ForAll String string1, @ForAll String string2
    ) {
        String conc = string1 + string2;
        Assertions.assertTrue(conc.length() >= string1.length());
        Assertions.assertTrue(conc.length() >= string2.length());
    }
}
