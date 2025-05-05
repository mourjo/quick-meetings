package me.mourjo.quickmeetings.web.dto;

import java.util.List;

public record MeetingInviteCreationRequest(long meetingId, List<Long> invitees) {

}
