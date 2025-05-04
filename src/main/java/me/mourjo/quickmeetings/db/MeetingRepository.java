package me.mourjo.quickmeetings.db;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends CrudRepository<Meeting, Long> {

    @Query("""
         SELECT
            existing_meeting.id,
            existing_meeting.name,
            existing_meeting.from_ts,
            existing_meeting.to_ts,
            existing_meeting.created_ts,
            existing_meeting.updated_ts
        FROM
         meetings existing_meeting JOIN user_meetings um ON existing_meeting.id = um.meeting_id
         WHERE um.user_id= :userId
        """)
    List<Meeting> findAllMeetingsForUser(@Param("userId") long userId);

    @Query("""
         SELECT
            existing_meeting.id,
            existing_meeting.name,
            existing_meeting.from_ts,
            existing_meeting.to_ts,
            existing_meeting.created_ts,
            existing_meeting.updated_ts
        FROM
         meetings existing_meeting JOIN user_meetings um ON existing_meeting.id = um.meeting_id
         WHERE um.user_id= :userId
         AND (existing_meeting.from_ts <= :to AND existing_meeting.to_ts >= :from)
        """)
    List<Meeting> findOverlappingMeetingsForUser(
        @Param("userId") long userId,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );
}
