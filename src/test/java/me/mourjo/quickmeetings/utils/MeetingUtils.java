package me.mourjo.quickmeetings.utils;

import static org.assertj.core.api.Assertions.assertThat;

import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.db.UserMeeting.RoleOfUser;
import me.mourjo.quickmeetings.db.UserMeetingRepository;
import me.mourjo.quickmeetings.db.UserRepository;
import me.mourjo.quickmeetings.service.MeetingsService;
import me.mourjo.quickmeetings.service.UserService;

public class MeetingUtils {

    private final MeetingsService meetingsService;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserMeetingRepository userMeetingRepository;

    public MeetingUtils(UserService userService, MeetingsService meetingsService,
        MeetingRepository meetingRepository, UserRepository userRepository,
        UserMeetingRepository userMeetingRepository) {

        this.meetingsService = meetingsService;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.userMeetingRepository = userMeetingRepository;
        this.userService = userService;
    }

    public void validateMeetingRole(long meetingId, long userId, RoleOfUser role) {
        var userMeetings = userMeetingRepository.findAllByMeetingId(meetingId);
        var meetingRoles = userMeetings.stream()
            .filter(um -> um.userId() == userId)
            .toList();
        assertThat(meetingRoles.size()).isEqualTo(1);
        assertThat(meetingRoles.get(0).userRole()).isEqualTo(role);
    }

    public void validateMeetingName(long meetingId, String name) {
        var maybeMeeting = meetingRepository.findById(meetingId);
        assertThat(maybeMeeting).isPresent();

        var meeting = maybeMeeting.get();
        assertThat(meeting.name()).isEqualTo(name);
    }
}
