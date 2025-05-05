package me.mourjo.quickmeetings.db;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface UserMeetingRepository extends CrudRepository<UserMeeting, Long> {

    List<UserMeeting> findByMeetingIdIn(List<Long> meetingIds);

    @Query("""
        SELECT *
        FROM user_meetings
        WHERE meeting_id IN (:meetingIds)
        """)
    List<UserMeeting> meetingAttendees(@Param("meetingIds") List<Long> meetingIds);

    default List<UserMeeting> findAllByMeetingId(Long meetingId) {
        return findByMeetingIdIn(List.of(meetingId));
    }

}
