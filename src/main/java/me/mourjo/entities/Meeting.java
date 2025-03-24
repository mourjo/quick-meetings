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

}
