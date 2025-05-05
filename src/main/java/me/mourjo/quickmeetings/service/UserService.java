package me.mourjo.quickmeetings.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String name) {
        return userRepository.save(
            User.builder().name(name).build()
        );
    }

    public User getUser(long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public List<User> getUsers(List<Long> ids) {
        List<User> users = new ArrayList<>();
        userRepository.findAllById(ids).forEach(users::add);
        return Collections.unmodifiableList(users);
    }
}
