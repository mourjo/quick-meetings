package me.mourjo.quickmeetings.exceptions;

import java.time.ZonedDateTime;

public class StartAfterEndException extends GenericMeetingException {

    private final ZonedDateTime from, to;

    public StartAfterEndException(ZonedDateTime from, ZonedDateTime zonedDateTime) {
        this.from = from;
        to = zonedDateTime;
    }

    @Override
    public String getMessage() {
        return "Meeting cannot start (%s) after its end time (%s)".formatted(from, to);
    }
}
