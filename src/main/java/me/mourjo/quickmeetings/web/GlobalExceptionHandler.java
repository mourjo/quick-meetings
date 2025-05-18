package me.mourjo.quickmeetings.web;

import me.mourjo.quickmeetings.exceptions.GenericMeetingException;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
import me.mourjo.quickmeetings.exceptions.UserNameNotAllowedException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import me.mourjo.quickmeetings.web.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> userNotFound(UserNotFoundException uex) {
        return ResponseEntity
            .status(404)
            .body(new ErrorResponse("Users %s not found".formatted(uex.getUserIds())));
    }

    @ExceptionHandler(MeetingNotFoundException.class)
    public ResponseEntity<ErrorResponse> meetingNotFound(MeetingNotFoundException uex) {
        return ResponseEntity
            .status(404)
            .body(new ErrorResponse("Meeting %s not found".formatted(uex.getMeetingId())));
    }

    @ExceptionHandler(GenericMeetingException.class)
    public ResponseEntity<ErrorResponse> handleMeetingExceptions(GenericMeetingException ex) {
        return ResponseEntity
            .status(400)
            .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(UserNameNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleUserNameNotAllowedException(
        UserNameNotAllowedException ex) {
        return ResponseEntity
            .status(400)
            .body(new ErrorResponse(ex.getMessage()));
    }
}
