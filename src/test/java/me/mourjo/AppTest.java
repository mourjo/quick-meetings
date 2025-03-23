package me.mourjo;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AppTest {

    @Test
    public void testMe() {
        Assertions.assertEquals("Hello, world!", App.saySomething());
    }
}
