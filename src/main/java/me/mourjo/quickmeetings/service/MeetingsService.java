package me.mourjo.quickmeetings.service;

import java.time.ZonedDateTime;
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

    public boolean invite(long meetingId, long userId) {
        return invite(meetingId, List.of(userId));
    }

    public boolean invite(long meetingId, List<Long> users) {
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
            return false;
        }

        var existingAttendees = userMeetingRepository.meetingAttendees(List.of(meetingId))
            .stream()
            .map(UserMeeting::userId)
            .collect(Collectors.toSet());

        for (var userId : users) {
            if (!existingAttendees.contains(userId)) {
                var invite = UserMeeting.builder()
                    .meetingId(meetingId)
                    .userId(userId)
                    .userRole(RoleOfUser.INVITED)
                    .build();
                userMeetingRepository.save(invite);
            }
        }

        return true;
    }

    public long createMeeting(String name, long userId, ZonedDateTime from, ZonedDateTime to) {
        var maybeUser = userRepository.findById(userId);
        if (maybeUser.isPresent()) {
            var overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
                userId,
                from.toOffsetDateTime(),
                to.toOffsetDateTime()
            );

            if (!overlappingMeetings.isEmpty()) {
                throw new OverlappingMeetingsException();
            }

            var meeting = meetingRepository.save(
                Meeting.builder()
                    .startAt(from.toOffsetDateTime())
                    .endAt(to.toOffsetDateTime())
                    .name(name)
                    .build()
            );

            userMeetingRepository.save(
                UserMeeting.builder()
                    .userId(userId)
                    .meetingId(meeting.id())
                    .build()
            );
            return meeting.id();
        }

        throw new UserNotFoundException(userId);
    }

    public boolean accept(long meetingId, long userId) {
        return userMeetingRepository.acceptInvite(meetingId, userId) == 1;
    }
}
