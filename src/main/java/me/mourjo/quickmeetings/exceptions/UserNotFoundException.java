package me.mourjo.quickmeetings.exceptions;

import lombok.Getter;

@Getter
public class UserNotFoundException extends GenericMeetingException {

    private final long userId;

    public UserNotFoundException(long userId) {
        this.userId = userId;
    }
}
