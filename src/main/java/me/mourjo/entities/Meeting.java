package me.mourjo.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jooq.Field;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.impl.DSL;

@NoArgsConstructor
@Getter
@Table(name = "meetings")
public class Meeting {

    @Column(name = "id")
    long id;

    @Column(name = "name")
    String name;

    @Column(name = "start_at")
    OffsetDateTime startTime;

    @Column(name = "end_at")
    OffsetDateTime endTime;

    @Column(name = "created_at")
    OffsetDateTime createdAt;

    @Column(name = "updated_at")
    OffsetDateTime updatedAt;

    public static Field<Long> idField() {
        return DSL.field("id", Long.class);
    }

    public static Field<String> nameField() {
        return DSL.field("name", String.class);
    }

    public static Field<OffsetDateTime> startTimeField() {
        return DSL.field("start_at", OffsetDateTime.class);
    }

    public static Field<OffsetDateTime> endTimeField() {
        return DSL.field("end_at", OffsetDateTime.class);
    }

    public static Field<OffsetDateTime> createdAtField() {
        return DSL.field("created_at", OffsetDateTime.class);
    }

    public static Field<OffsetDateTime> updatedAtField() {
        return DSL.field("updated_at", OffsetDateTime.class);
    }

    public static org.jooq.Table<org.jooq.Record> table() {
        return DSL.table("meetings");
    }

    public static SelectFieldOrAsterisk[] asterisk() {
        return new SelectFieldOrAsterisk[]{
            idField(),
            nameField(),
            startTimeField(),
            endTimeField(),
            createdAtField(),
            updatedAtField(),
        };
    }

    @Override
    public String toString() {
        return "Meeting{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", startTime=" + startTime +
            ", endTime=" + endTime +
            '}';
    }

}
