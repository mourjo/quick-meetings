package me.mourjo.quickmeetings.db;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface UserMeetingRepository extends CrudRepository<UserMeeting, Long> {

    List<UserMeeting> findByMeetingIdIn(List<Long> meetingIds);

    default List<UserMeeting> findAllByMeetingId(Long meetingId) {
        return findByMeetingIdIn(List.of(meetingId));
    }

}
