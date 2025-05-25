package me.mourjo.quickmeetings.web.dto;

import jakarta.validation.constraints.NotNull;

public record MeetingCreationRequest(long userId,
                                     @NotNull(message = "Name of the meeting cannot be null") String name,
                                     @NotNull(message = "Meeting duration cannot be null") MeetingDuration duration,
                                     @NotNull(message = "Timezone cannot be null") String timezone) {

}
