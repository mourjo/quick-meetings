package me.mourjo.quickmeetings.db;

import java.time.OffsetDateTime;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Table("user_meetings")
public record UserMeeting(
    @Column("id") @Id long id,
    @Column("user_id") long userId,
    @Column("meeting_id") long meetingId,
    @Column("role_of_user") RoleOfUser userRole,
    @Column("updated_ts") OffsetDateTime updatedAt) {

    public UserMeeting {
        if (userRole == null) {
            userRole = RoleOfUser.OWNER;
        }

        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    public static UserMeetingBuilder buildFrom(UserMeeting other) {
        return UserMeeting.builder()
            .id(other.id())
            .userId(other.userId())
            .meetingId(other.meetingId())
            .userRole(other.userRole());
    }

    public enum RoleOfUser {
        OWNER,
        INVITED,
        ACCEPTED,
        REJECTED
    }
}
