package me.mourjo.quickmeetings.db;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends CrudRepository<Meeting, Long> {

    @Query("""
         SELECT
            meeting.id,
            meeting.name,
            meeting.from_ts,
            meeting.to_ts,
            meeting.created_ts,
            meeting.updated_ts
        FROM
         meetings meeting JOIN user_meetings um ON meeting.id = um.meeting_id
         WHERE um.user_id= :userId
        """)
    List<Meeting> findAllMeetingsForUser(@Param("userId") long userId);
}
