package me.mourjo.quickmeetings.service;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.User;
import me.mourjo.quickmeetings.db.UserMeeting;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
import me.mourjo.quickmeetings.exceptions.OverlappingMeetingsException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MeetingsService {

    private final UserService userService;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final UserMeetingRepository userMeetingRepository;

    public MeetingsService(UserService userService, MeetingRepository meetingRepository,
        UserRepository userRepository,
        UserMeetingRepository userMeetingRepository) {
        this.userService = userService;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.userMeetingRepository = userMeetingRepository;
    }

    public List<UserMeeting> invite(long meetingId, long userId) {
        return invite(meetingId, List.of(userId));
    }

    public List<UserMeeting> invite(long meetingId, List<Long> users) {
        var meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new MeetingNotFoundException(meetingId));

        var existingUsers = userService.getUsers(users).stream()
            .map(User::id)
            .collect(Collectors.toSet());

        if (!existingUsers.containsAll(users)) {
            var nonExistingUsers = users.stream().filter(userId -> !existingUsers.contains(userId))
                .toList();
            throw new UserNotFoundException(nonExistingUsers);
        }

        var overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            users,
            meeting.startAt(),
            meeting.endAt()
        );

        if (!overlappingMeetings.isEmpty()) {
            throw new OverlappingMeetingsException();
        }

        var existingAttendees = userMeetingRepository.meetingAttendees(List.of(meetingId))
            .stream()
            .map(UserMeeting::userId)
            .collect(Collectors.toSet());

        List<UserMeeting> createdUserMeetings = new ArrayList<>();

        for (var userId : users) {
            if (!existingAttendees.contains(userId)) {
                var invite = UserMeeting.builder()
                    .meetingId(meetingId)
                    .userId(userId)
                    .userRole(RoleOfUser.INVITED)
                    .build();
                createdUserMeetings.add(userMeetingRepository.save(invite));
            }
        }

        return createdUserMeetings;
    }

    public Meeting createMeeting(String name, long userId, ZonedDateTime from, ZonedDateTime to) {
        return createMeeting(name, userId, from.toOffsetDateTime(), to.toOffsetDateTime());
    }

    public Meeting createMeeting(String name, long userId, OffsetDateTime from, OffsetDateTime to) {
        var maybeUser = userRepository.findById(userId);
        if (maybeUser.isPresent()) {
            var overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
                userId,
                from,
                to
            );

            if (!overlappingMeetings.isEmpty()) {
                throw new OverlappingMeetingsException();
            }

            var meeting = meetingRepository.save(
                Meeting.builder()
                    .startAt(from)
                    .endAt(to)
                    .name(name)
                    .build()
            );

            userMeetingRepository.save(
                UserMeeting.builder()
                    .userId(userId)
                    .meetingId(meeting.id())
                    .build()
            );
            return meeting;
        }

        throw new UserNotFoundException(userId);
    }

    public boolean accept(long meetingId, long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        var meetingMaybe = meetingRepository.findById(meetingId);
        if (meetingMaybe.isPresent()) {
            var meeting = meetingMaybe.get();
            var overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(userId,
                meeting.startAt(), meeting.endAt());

            if (!overlappingMeetings.isEmpty()) {
                throw new OverlappingMeetingsException();
            }

            return userMeetingRepository.acceptInvite(meetingId, userId) == 1;
        }
        throw new MeetingNotFoundException(meetingId);
    }

    public boolean reject(long meetingId, long userId) {
        return userMeetingRepository.rejectInvite(meetingId, userId) == 1;
    }
}
