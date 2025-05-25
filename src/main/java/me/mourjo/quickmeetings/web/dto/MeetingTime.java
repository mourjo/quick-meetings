package me.mourjo.quickmeetings.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

public record MeetingTime(@NotNull(message = "Date cannot be null") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @NotNull(message = "Time cannot be null") @DateTimeFormat(pattern = "hh:mm:ss") LocalTime time) {

}
