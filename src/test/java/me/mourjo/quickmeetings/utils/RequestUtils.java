package me.mourjo.quickmeetings.utils;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class RequestUtils {

    public static MockHttpServletRequestBuilder meetingRequest(long userId, String meetingName,
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
