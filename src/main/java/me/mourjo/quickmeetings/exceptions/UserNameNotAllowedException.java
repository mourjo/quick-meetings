package me.mourjo.quickmeetings.exceptions;

public class UserNameNotAllowedException extends GenericMeetingException {

    private final String name;

    public UserNameNotAllowedException(String name) {
        this.name = name;
    }

    @Override
    public String getMessage() {
        return "Username %s not allowed".formatted(name);
    }
}
