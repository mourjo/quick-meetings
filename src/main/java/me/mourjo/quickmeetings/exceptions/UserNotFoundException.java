package me.mourjo.quickmeetings.exceptions;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class UserNotFoundException extends GenericMeetingException {

    private final List<Long> userIds = new ArrayList<>();

    public UserNotFoundException(long userId) {
        this.userIds.add(userId);
    }

    public UserNotFoundException(List<Long> userIds) {
        this.userIds.addAll(userIds);
    }

    @Override
    public String getMessage() {
        return "Users %s not found".formatted(userIds);
    }
}
