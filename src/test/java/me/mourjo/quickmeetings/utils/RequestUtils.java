package me.mourjo.quickmeetings.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class RequestUtils {

    public static MockHttpServletRequestBuilder meetingRequest(long userId, String meetingName,
        LocalDateTime from, LocalDateTime to, String timezone) {

        var fromDate = from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        var fromTime = from.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        var toDate = to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        var toTime = to.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        return MockMvcRequestBuilders.post("/meeting")
            .content("""
                {
                  "userId": %d,
                  "name": "%s",
                  "duration": {
                    "from": {
                      "date": "%s",
                      "time": "%s"
                    },
                    "to": {
                      "date": "%s",
                      "time": "%s"
                    }
                  },
                  "timezone": "%s"
                }
                """.formatted(
                userId,
                meetingName,
                fromDate,
                fromTime,
                toDate,
                toTime,
                timezone
            ))
            .contentType(MediaType.APPLICATION_JSON);

    }
}
