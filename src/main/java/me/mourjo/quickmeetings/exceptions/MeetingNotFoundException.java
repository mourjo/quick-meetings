package me.mourjo.quickmeetings.exceptions;

import lombok.Getter;

@Getter
public class MeetingNotFoundException extends GenericMeetingException {

    long meetingId;

    public MeetingNotFoundException(long meetingId) {
        this.meetingId = meetingId;
    }

    @Override
    public String getMessage() {
        return "Meeting %s not found".formatted(meetingId);
    }
}
