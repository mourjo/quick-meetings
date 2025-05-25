package me.mourjo.quickmeetings.web.dto;

import jakarta.validation.constraints.NotNull;

public record MeetingDuration(
    @NotNull(message = "Meeting duration `from` cannot be null") MeetingTime from,
    @NotNull(message = "Meeting duration `to` cannot be null") MeetingTime to) {

}
