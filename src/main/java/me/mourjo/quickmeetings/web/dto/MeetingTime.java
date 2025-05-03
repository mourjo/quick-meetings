package me.mourjo.quickmeetings.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

public record MeetingTime(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @DateTimeFormat(pattern = "hh:mm:ss") LocalTime time) {

}
