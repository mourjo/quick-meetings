package me.mourjo.quickmeetings.db;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface UserMeetingRepository extends ListCrudRepository<UserMeeting, Long> {

    List<UserMeeting> findByMeetingIdIn(List<Long> meetingIds);

    @Query("""
        SELECT *
        FROM user_meetings
        WHERE meeting_id IN (:meetingIds)
        """)
    List<UserMeeting> meetingAttendees(@Param("meetingIds") List<Long> meetingIds);

    @Modifying
    @Query("""
        UPDATE user_meetings
        SET role_of_user = 'ACCEPTED', updated_ts=NOW()
        WHERE user_id = :userId AND meeting_id = :meetingId
        AND role_of_user IN ('INVITED')
        """)
    int acceptInvite(@Param("meetingId") long meetingId, @Param("userId") long userId);

    @Modifying
    @Query("""
        UPDATE user_meetings
        SET role_of_user = 'REJECTED', updated_ts=NOW()
        WHERE user_id = :userId AND meeting_id = :meetingId
        """)
    int rejectInvite(@Param("meetingId") long meetingId, @Param("userId") long userId);

    default List<UserMeeting> findAllByMeetingId(Long meetingId) {
        return findByMeetingIdIn(List.of(meetingId));
    }

}
