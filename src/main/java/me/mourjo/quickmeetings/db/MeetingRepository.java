package me.mourjo.quickmeetings.db;

import java.time.OffsetDateTime;
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
         AND (
               (meeting.from_ts <= :from AND meeting.to_ts >= :from)
            OR (meeting.from_ts <= :to AND meeting.to_ts >= :to)
            )
        """)
    List<Meeting> findOverlappingMeetingsForUser(
        @Param("userId") long userId,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );
}
