package me.mourjo.repository;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.SneakyThrows;
import me.mourjo.common.Database;
import me.mourjo.entities.Meeting;
import me.mourjo.entities.generated.tables.Meetings;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class MeetingRepository {

    @SneakyThrows
    public int insert(String name, OffsetDateTime from, OffsetDateTime to) {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .insertInto(Meetings.MEETINGS)
                .columns(Meetings.MEETINGS.NAME, Meetings.MEETINGS.START_AT,
                    Meetings.MEETINGS.END_AT)
                .values(name, from, to)
                .execute();
        }
    }

    @SneakyThrows
    public List<Meeting> meetingsInRange(OffsetDateTime ts) {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .select(DSL.asterisk())
                .from(Meetings.MEETINGS)
                .where(Meetings.MEETINGS.START_AT.greaterOrEqual(ts)
                    .and(Meetings.MEETINGS.END_AT.lessOrEqual(ts)))
                .fetchInto(Meeting.class);
        }
    }

    @SneakyThrows
    public boolean exists(OffsetDateTime start, OffsetDateTime end) {
        try (Connection conn = Database.getConnection()) {
            var conflictingMeetings = DSL.using(conn, SQLDialect.POSTGRES)
                .select(Meetings.MEETINGS.ID)
                .from(Meetings.MEETINGS)
                .where(
                    (
                        Meetings.MEETINGS.END_AT.greaterOrEqual(start)
                    ).and(
                        Meetings.MEETINGS.START_AT.lessOrEqual(end)
                    )
                )
                .limit(1)
                .fetchInto(Meeting.class);

            return !conflictingMeetings.isEmpty();
        }
    }

    @SneakyThrows
    public int deleteAll() {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .deleteFrom(Meetings.MEETINGS)
                .execute();
        }
    }


    @SneakyThrows
    public List<Meeting> fetch(String name) {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .select(DSL.asterisk())
                .from(Meetings.MEETINGS)
                .where(Meetings.MEETINGS.NAME.eq(name))
                .fetchInto(Meeting.class);
        }
    }

    @SneakyThrows
    public int delete(int id) {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .deleteFrom(Meetings.MEETINGS)
                .where(Meetings.MEETINGS.ID.eq(id))
                .execute();
        }
    }

    @SneakyThrows
    public List<Meeting> fetchAll() {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .select(DSL.asterisk())
                .from(Meetings.MEETINGS)
                .fetchInto(Meeting.class);
        }
    }
}
