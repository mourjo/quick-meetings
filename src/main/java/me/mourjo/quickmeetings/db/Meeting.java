package me.mourjo.quickmeetings.db;

import java.time.OffsetDateTime;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("meetings")
@Builder
public record Meeting(
    @Id long id,
    String name,
    @Column("from_ts") OffsetDateTime startAt,
    @Column("to_ts") OffsetDateTime endAt,
    @Column("created_ts") OffsetDateTime createdAt,
    @Column("updated_ts") OffsetDateTime updatedAt

) {

    public Meeting {
        var now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public static MeetingBuilder buildFrom(Meeting other) {
        return Meeting.builder()
            .id(other.id())
            .startAt(other.startAt())
            .endAt(other.endAt())
            .name(other.name())
            .createdAt(other.createdAt())
            .updatedAt(other.updatedAt());
    }
}
