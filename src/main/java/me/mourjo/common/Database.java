package me.mourjo.common;

import java.sql.Connection;
import java.sql.DriverManager;
import lombok.SneakyThrows;

public class Database {

    @SneakyThrows
    public static Connection getConnection() {
        String host = Environment.getPostgresHost();
        String port = Environment.getPostgresPort();
        String database = Environment.getPostgresDatabase();
        String username = Environment.getPostgresUser();
        String connectionString = "jdbc:postgresql://%s:%s/%s".formatted(host, port, database);
        return DriverManager.getConnection(connectionString, username, null);
    }
}
