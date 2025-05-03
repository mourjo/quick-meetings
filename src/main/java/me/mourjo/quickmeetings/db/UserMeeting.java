package me.mourjo.quickmeetings.db;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Table("user_meetings")
public record UserMeeting(@Id long id, long userId, long meetingId) {

}
