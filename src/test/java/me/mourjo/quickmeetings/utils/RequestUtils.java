package me.mourjo.quickmeetings.utils;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class RequestUtils {

    public static MockHttpServletRequestBuilder meetingRequest(long userId, String meetingName,
        TemporalAccessor from, TemporalAccessor to, String timezone) {

        var fromDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(from);
        var fromTime = DateTimeFormatter.ofPattern("HH:mm:ss").format(from);

        var toDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(to);
        var toTime = DateTimeFormatter.ofPattern("HH:mm:ss").format(to);

        return requestBody(
            userId,
            meetingName,
            fromDate,
            fromTime,
            toDate,
            toTime,
            timezone
        );
    }

    private static MockHttpServletRequestBuilder requestBody(long userId, String meetingName,
        String fromDate, String fromTime, String toDate, String toTime, String timezone) {
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
