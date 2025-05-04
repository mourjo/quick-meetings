package me.mourjo.quickmeetings.web.dto;

import java.util.List;

public record MeetingInviteRequest(long meetingId, List<Long> invitees) {

}
