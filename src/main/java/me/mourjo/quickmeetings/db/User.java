package me.mourjo.quickmeetings.db;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Table("users")
public record User(String name, @Id long id) {

}
