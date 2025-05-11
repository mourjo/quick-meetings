package me.mourjo.quickmeetings.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import me.mourjo.quickmeetings.exceptions.IllegalMeetingDurationException;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;
import me.mourjo.quickmeetings.web.dto.MeetingCreationRequest;
import me.mourjo.quickmeetings.web.dto.MeetingCreationResponse;
import me.mourjo.quickmeetings.web.dto.MeetingInviteAcceptanceRequest;
import me.mourjo.quickmeetings.web.dto.MeetingInviteAcceptanceResponse;
import me.mourjo.quickmeetings.web.dto.MeetingInviteCreationRequest;
import me.mourjo.quickmeetings.web.dto.MeetingInviteCreationResponse;
import me.mourjo.quickmeetings.web.dto.MeetingRejectionRequest;
import me.mourjo.quickmeetings.web.dto.MeetingRejectionResponse;
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
    ResponseEntity<MeetingInviteCreationResponse> invite(
        @RequestBody MeetingInviteCreationRequest request) {
        try {
            meetingsService.invite(request.meetingId(), request.invitees());
            return ResponseEntity.ok(new MeetingInviteCreationResponse("Invited successfully"));
        } catch (OverlappingMeetingsException ex) {
            return ResponseEntity.status(400)
                .body(new MeetingInviteCreationResponse("Users have conflicts"));
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

        if (!fromZdt.isBefore(toZdt)) {
            throw new IllegalMeetingDurationException(fromZdt, toZdt);
        }

        var meeting = meetingsService.createMeeting(
            request.name(),
            request.userId(),
            fromZdt,
            toZdt
        );

        var userName = userService.getUser(request.userId()).name();

        return ResponseEntity.ok(new MeetingCreationResponse(
            "Meeting created by %s from %s to %s".formatted(userName, fromZdt, toZdt),
            request.name(),
            meeting.id()
        ));
    }

    @PostMapping("/meeting/accept")
    ResponseEntity<MeetingInviteAcceptanceResponse> accept(
        @RequestBody MeetingInviteAcceptanceRequest request) {
        if (meetingsService.accept(request.meetingId(), request.userId())) {
            return ResponseEntity.ok(
                new MeetingInviteAcceptanceResponse("Accepted successfully"));
        }

        return ResponseEntity.status(400)
            .body(new MeetingInviteAcceptanceResponse("Failed to accept invite"));
    }

    @PostMapping("/meeting/reject")
    ResponseEntity<MeetingRejectionResponse> reject(
        @RequestBody MeetingRejectionRequest request) {
        if (meetingsService.reject(request.meetingId(), request.userId())) {
            return ResponseEntity.ok(
                new MeetingRejectionResponse("Rejected successfully"));
        }

        return ResponseEntity.status(400)
            .body(new MeetingRejectionResponse("Failed to reject invite"));
    }

}
