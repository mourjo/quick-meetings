package me.mourjo.quickmeetings.service;

import java.time.ZonedDateTime;
import me.mourjo.quickmeetings.db.Meeting;
import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.UserMeeting;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
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
