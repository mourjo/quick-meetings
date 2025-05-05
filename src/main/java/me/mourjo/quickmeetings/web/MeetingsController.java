package me.mourjo.quickmeetings.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.web.dto.MeetingCreationRequest;
import me.mourjo.quickmeetings.web.dto.MeetingCreationResponse;
import me.mourjo.quickmeetings.web.dto.MeetingInviteRequest;
import me.mourjo.quickmeetings.web.dto.MeetingInviteResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeetingsController {

    private final MeetingsService meetingsService;
    private final UserService userService;

    public MeetingsController(MeetingsService meetingsService, UserService userService) {
        this.meetingsService = meetingsService;
        this.userService = userService;
    }

    @PostMapping("/meeting/invite")
    ResponseEntity<MeetingInviteResponse> invite(@RequestBody MeetingInviteRequest request) {
        try {
            var result = meetingsService.invite(request.meetingId(),
                request.invitees());

            if (result) {
                return ResponseEntity.ok(new MeetingInviteResponse("Invited successfully"));
            }

            return ResponseEntity.status(400).body(new MeetingInviteResponse(
                "Users have conflicts"));
        } catch (DbActionExecutionException ex) {
            if (ex.getCause() instanceof DuplicateKeyException) {
                return ResponseEntity.status(400).body(new MeetingInviteResponse(
                    "duplicatee"));
            }
            return ResponseEntity.status(400).body(new MeetingInviteResponse(
                "something changed: " + ex.getCause()));
        }

    }

    @PostMapping("/meeting")
    ResponseEntity<MeetingCreationResponse> createMeeting(
        @RequestBody MeetingCreationRequest request) {

        var from = request.duration().from();
        var fromLdt = LocalDateTime.of(from.date(), from.time());
        var fromZdt = ZonedDateTime.of(fromLdt, ZoneId.of(request.timezone()));

        var to = request.duration().to();
        var toLdt = LocalDateTime.of(to.date(), to.time());
        var toZdt = ZonedDateTime.of(toLdt, ZoneId.of(request.timezone()));

        var meetingId = meetingsService.createMeeting(
            request.name(),
            request.userId(),
            fromZdt,
            toZdt
        );

        var userName = userService.getUser(request.userId()).name();

        return ResponseEntity.ok(new MeetingCreationResponse(
            "Meeting created by %s from %s to %s".formatted(userName, fromZdt, toZdt),
            request.name(),
            meetingId
        ));
    }

}
