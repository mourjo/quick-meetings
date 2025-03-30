package me.mourjo.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Table(name = "meetings")
public class Meeting {

    @Column(name = "id")
    int id;

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
