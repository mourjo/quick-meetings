package me.mourjo.repository;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.SneakyThrows;
import me.mourjo.common.Database;
import me.mourjo.entities.Meeting;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class MeetingRepository {
    @SneakyThrows
    public int insert(String name, OffsetDateTime from, OffsetDateTime to) {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .insertInto(Meeting.table())
                .columns(Meeting.nameField(), Meeting.startTimeField(), Meeting.endTimeField())
                .values(name, from, to)
                .execute();
        }
    }


    @SneakyThrows
    public List<Meeting> fetch(String name) {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .select(Meeting.asterisk())
                .from(Meeting.table())
                .where(Meeting.nameField().eq(name))
                .fetchInto(Meeting.class);
        }
    }

    @SneakyThrows
    public int delete(long id) {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .deleteFrom(Meeting.table())
                .where(Meeting.idField().eq(id))
                .execute();
        }
    }

    @SneakyThrows
    public List<Meeting> fetchAll() {
        try (Connection conn = Database.getConnection()) {
            return DSL.using(conn, SQLDialect.POSTGRES)
                .select(Meeting.asterisk())
                .from(Meeting.table())
                .fetchInto(Meeting.class);
        }
    }
}
