package me.mourjo.quickmeetings.exceptions;

import java.time.ZonedDateTime;

public class IllegalMeetingDurationException extends GenericMeetingException {

    private final ZonedDateTime from, to;

    public IllegalMeetingDurationException(ZonedDateTime from, ZonedDateTime zonedDateTime) {
        this.from = from;
        to = zonedDateTime;
    }

    @Override
    public String getMessage() {
        return "Duration from %s to %s is invalid".formatted(from, to);
    }
}
