package me.mourjo.common;

import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.postgresql.ds.PGSimpleDataSource;

public class Database {

    @SneakyThrows
    public static DataSource getDataSource() {
        String host = Environment.getPostgresHost();
        String port = Environment.getPostgresPort();
        String database = Environment.getPostgresDatabase();
        String username = Environment.getPostgresUser();
        String connectionString = "jdbc:postgresql://%s:%s/%s".formatted(host, port, database);

        var dataSource = new PGSimpleDataSource();
        int portNum = Integer.parseInt(port);

        dataSource.setServerNames(new String[]{host});
        dataSource.setPortNumbers(new int[]{portNum});
        dataSource.setDatabaseName(database);
        dataSource.setUser(username);
        dataSource.setPassword("");

        //return DriverManager.getConnection(connectionString, username, null);
        return dataSource;
    }
}
