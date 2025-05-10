package me.mourjo.quickmeetings.utils;

import com.jayway.jsonpath.JsonPath;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class RequestUtils {

    public static MockHttpServletRequestBuilder meetingCreationRequest(long userId,
        String meetingName,
        TemporalAccessor from, TemporalAccessor to, String timezone) {

        var fromDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(from);
        var fromTime = DateTimeFormatter.ofPattern("HH:mm:ss").format(from);

        var toDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(to);
        var toTime = DateTimeFormatter.ofPattern("HH:mm:ss").format(to);

        return meetingCreationBody(
            userId,
            meetingName,
            fromDate,
            fromTime,
            toDate,
            toTime,
            timezone
        );
    }

    public static MockHttpServletRequestBuilder inviteCreationRequest(long meetingId,
        List<Long> invitees) {
        return inviteCreationBody(meetingId, invitees);
    }

    public static MockHttpServletRequestBuilder inviteAcceptanceRequest(long meetingId,
        long userId) {
        return invitationAcceptanceBody(meetingId, userId);
    }

    public static MockHttpServletRequestBuilder inviteRejectionRequest(long meetingId,
        long userId) {
        return invitationRejectionBody(meetingId, userId);
    }

    private static MockHttpServletRequestBuilder invitationAcceptanceBody(long meetingId,
        long userId) {
        return MockMvcRequestBuilders.post("/meeting/accept")
            .content("""
                {
                  "meetingId": %s,
                  "userId": %s
                }
                """.formatted(
                meetingId,
                userId
            )).contentType(MediaType.APPLICATION_JSON);
    }

    private static MockHttpServletRequestBuilder invitationRejectionBody(long meetingId,
        long userId) {
        return MockMvcRequestBuilders.post("/meeting/reject")
            .content("""
                {
                  "meetingId": %s,
                  "userId": %s
                }
                """.formatted(
                meetingId,
                userId
            )).contentType(MediaType.APPLICATION_JSON);
    }

    private static MockHttpServletRequestBuilder inviteCreationBody(long meetingId,
        List<Long> invitees) {
        return MockMvcRequestBuilders.post("/meeting/invite")
            .content("""
                {
                  "meetingId": %s,
                  "invitees": %s
                }
                """.formatted(
                meetingId,
                invitees.toString()
            ))
            .contentType(MediaType.APPLICATION_JSON);
    }

    private static MockHttpServletRequestBuilder meetingCreationBody(long userId,
        String meetingName,
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

    @SneakyThrows
    public static String readJsonPath(MvcResult result, String path) {
        return JsonPath.read(result.getResponse().getContentAsString(), path)
            .toString();
    }
}
