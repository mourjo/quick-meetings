package me.mourjo.quickmeetings.web;

import me.mourjo.quickmeetings.exceptions.UserNameNotAllowedException;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.web.dto.UserCreationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController {

    private final UserService userService;

    public UsersController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/user")
    ResponseEntity<UserCreationResponse> createUser(String name)
        throws UserNameNotAllowedException {
        if (name != null && !name.isEmpty()) {
            var dbUser = userService.createUser(name);
            return ResponseEntity.ok(new UserCreationResponse(dbUser.id(), name));
        }
        throw new UserNameNotAllowedException(name);
    }
}
