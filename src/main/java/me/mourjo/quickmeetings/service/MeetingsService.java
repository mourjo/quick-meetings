package me.mourjo.quickmeetings.service;

import java.time.ZonedDateTime;
import java.util.List;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.UserMeeting;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.exceptions.MeetingNotFoundException;
import me.mourjo.quickmeetings.exceptions.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MeetingsService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final UserMeetingRepository userMeetingRepository;

    public MeetingsService(MeetingRepository meetingRepository, UserRepository userRepository,
        UserMeetingRepository userMeetingRepository) {
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.userMeetingRepository = userMeetingRepository;
    }

    public List<Long> invite(long meetingId, List<Long> users) {
        var meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new MeetingNotFoundException(meetingId));

        var overlappingMeetings = meetingRepository.findOverlappingMeetingsForUser(
            users,
            meeting.startAt(),
            meeting.endAt()
        );

        if (!overlappingMeetings.isEmpty()) {
            var problemMeetingIds = meetingRepository.findAllConfirmedMeetingsForUser(users)
                .stream()
                .map(Meeting::id)
                .toList();

            return userMeetingRepository.findByMeetingIdIn(problemMeetingIds)
                .stream().map(user -> user.userId()).toList();
        }

        for (var userId : users) {
            var invite = UserMeeting.builder()
                .meetingId(meetingId)
                .userId(userId)
                .userRole(RoleOfUser.INVITED)
                .build();
            userMeetingRepository.save(invite);
        }

        return users;
    }


    public long createMeeting(String name, long userId, ZonedDateTime from, ZonedDateTime to) {
        var maybeUser = userRepository.findById(userId);
        if (maybeUser.isPresent()) {
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
}
