package me.mourjo.quickmeetings.exceptions;

public class OverlappingMeetingsException extends GenericMeetingException {

    @Override
    public String getMessage() {
        return "Overlapping meetings exist";
    }
}
