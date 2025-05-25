package me.mourjo.quickmeetings.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.exceptions.GenericMeetingException;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
import me.mourjo.quickmeetings.exceptions.UserNameNotAllowedException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import me.mourjo.quickmeetings.web.dto.ErrorResponse;
import me.mourjo.quickmeetings.web.dto.ValidationErrorResponse;
import org.springframework.core.NestedRuntimeException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> userNotFound(UserNotFoundException uex) {
        return ResponseEntity
            .status(404)
            .body(new ErrorResponse("Users %s not found".formatted(uex.getUserIds())));
    }

    @SneakyThrows
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        List<String> fields = new ArrayList<>();
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            fields.add(fieldName);
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        String message = "Errors in request body: " + String.join(", ", fields);
        return ResponseEntity
            .status(400)
            .body(new ValidationErrorResponse(message, errors));
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

    @ExceptionHandler(exception = {NoResourceFoundException.class,
        HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleGeneric404s(Exception ex) {
        return ResponseEntity
            .status(404)
            .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(exception = {ServletException.class, RuntimeException.class,
        NestedRuntimeException.class, HttpMessageNotReadableException.class,
        IllegalArgumentException.class, Exception.class})
    public ResponseEntity<ErrorResponse> handleRuntimeExceptions(Exception ex) {
        return ResponseEntity
            .status(400)
            .body(new ErrorResponse(ex.getMessage()));
    }
}
