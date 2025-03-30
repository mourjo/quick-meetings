package me.mourjo.repository;

import static me.mourjo.entities.generated.tables.Meetings.MEETINGS;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.SneakyThrows;
import me.mourjo.common.Database;
import me.mourjo.entities.generated.tables.records.MeetingsRecord;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class MeetingRepository {

    private final DSLContext dsl;

    public MeetingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public MeetingRepository() {
        dsl = DSL.using(Database.getDataSource(), SQLDialect.POSTGRES);
    }

    @SneakyThrows
    public int insert(String name, OffsetDateTime from, OffsetDateTime to) {
        return dsl
            .insertInto(MEETINGS)
            .columns(MEETINGS.NAME, MEETINGS.START_AT,
                MEETINGS.END_AT)
            .values(name, from, to)
            .execute();
    }

    @SneakyThrows
    public List<MeetingsRecord> meetingsInRange(OffsetDateTime ts) {
        return dsl
            .selectFrom(MEETINGS)
            .where(
                (
                    MEETINGS.START_AT.greaterOrEqual(ts)
                ).and(
                    MEETINGS.END_AT.lessOrEqual(ts)
                )
            )
            .fetch();
    }

    @SneakyThrows
    public boolean exists(OffsetDateTime start, OffsetDateTime end) {
        var query = dsl.selectFrom(MEETINGS)
            .where(
                (
                    MEETINGS.END_AT.greaterOrEqual(start)
                ).and(
                    MEETINGS.START_AT.lessOrEqual(end)
                )
            );

        return dsl.fetchExists(query);
    }

    @SneakyThrows
    public int deleteAll() {
        return dsl
            .deleteFrom(MEETINGS)
            .execute();
    }


    @SneakyThrows
    public List<MeetingsRecord> fetch(String name) {
        return dsl
            .selectFrom(MEETINGS)
            .where(MEETINGS.NAME.eq(name))
            .fetch();
    }

    @SneakyThrows
    public int delete(int id) {
        return dsl
            .deleteFrom(MEETINGS)
            .where(MEETINGS.ID.eq(id))
            .execute();
    }

    @SneakyThrows
    public List<MeetingsRecord> fetchAll() {
        return dsl
            .selectFrom(MEETINGS)
            .fetch();
    }
}
