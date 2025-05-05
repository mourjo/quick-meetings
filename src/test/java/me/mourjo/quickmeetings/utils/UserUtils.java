package me.mourjo.quickmeetings.utils;

import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.UserService;

public class UserUtils {


    private final UserRepository userRepository;
    private final UserService userService;

    public UserUtils(UserService userService, UserRepository userRepository) {
        this.userRepository = userRepository;
        this.userService = userService;


    }
}
