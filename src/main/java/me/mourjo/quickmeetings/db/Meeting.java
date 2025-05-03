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

    public Meeting(long id, String name, OffsetDateTime startAt, OffsetDateTime endAt,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.startAt = startAt;
        this.endAt = endAt;
        var now = OffsetDateTime.now();

        if (createdAt != null) {
            this.createdAt = createdAt;
        } else {
            this.createdAt = now;
        }
        if (updatedAt != null) {
            this.updatedAt = updatedAt;
        } else {
            this.updatedAt = now;
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
