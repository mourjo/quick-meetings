package me.mourjo.quickmeetings.web.dto;

public record MeetingCreationRequest(long userId,
                                     String name,
                                     MeetingDuration duration,
                                     String timezone) {

}
