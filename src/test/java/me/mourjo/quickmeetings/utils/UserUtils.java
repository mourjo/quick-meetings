package me.mourjo.quickmeetings.utils;

import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.UserService;

public class UserUtils {

    public static User alice, bob, charlie, dick, erin, frank;
    private final UserRepository userRepository;
    private final UserService userService;

    public UserUtils(UserService userService, UserRepository userRepository) {
        this.userRepository = userRepository;
        this.userService = userService;

        alice = userService.createUser("Alice");
        bob = userService.createUser("Bob");
        charlie = userService.createUser("Charlie");
        dick = userService.createUser("Dick");
        erin = userService.createUser("Erin");
        frank = userService.createUser("Frank");
    }
}
