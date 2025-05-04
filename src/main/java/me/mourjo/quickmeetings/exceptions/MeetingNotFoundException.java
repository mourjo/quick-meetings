package me.mourjo.quickmeetings.exceptions;

import lombok.Getter;

@Getter
public class MeetingNotFoundException extends GenericMeetingException {

    long meetingId;

    public MeetingNotFoundException(long meetingId) {
        this.meetingId = meetingId;
    }
}
