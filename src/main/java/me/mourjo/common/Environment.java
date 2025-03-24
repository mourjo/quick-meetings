package me.mourjo.common;

import static me.mourjo.common.Constants.PG_DB_ENV_VAR;
import static me.mourjo.common.Constants.PG_HOST_ENV;
import static me.mourjo.common.Constants.PG_PORT_ENV_VAR;
import static me.mourjo.common.Constants.PG_USER_ENV_VAR;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Environment {

    private static Environment env = null;
    private final String postgresHost;
    private final String postgresPort;
    private final String postgresUser;
    private final String postgresDatabase;
    private final int serverPort;

    private Environment() {
        postgresHost = getEnv(PG_HOST_ENV, "localhost");
        postgresPort = getEnv(PG_PORT_ENV_VAR, "5432");
        postgresUser = getEnv(PG_USER_ENV_VAR, "justin");
        postgresDatabase = getEnv(PG_DB_ENV_VAR, "quick_meetings_test_db");

        String defaultPort = "8818";
        int port;
        try {
            port = Integer.parseInt(getEnv("SERVER_PORT", defaultPort));
        } catch (NumberFormatException e) {
            log.warn("Illegal port, falling back to default port {}", defaultPort);
            port = Integer.parseInt(defaultPort);
        }
        serverPort = port;
    }

    public static Environment getInstance() {
        if (env == null) {
            env = new Environment();
        }
        return env;
    }

    public static String getPostgresHost() {
        return getInstance().postgresHost;
    }

    public static String getPostgresPort() {
        return getInstance().postgresPort;
    }

    public static String getPostgresUser() {
        return getInstance().postgresUser;
    }

    public static String getPostgresDatabase() {
        return getInstance().postgresDatabase;
    }

    public static int getServerPort() {
        return getInstance().serverPort;
    }

    private static String getEnv(String environmentVar, String fallback) {
        String value = Optional.ofNullable(System.getenv(environmentVar)).orElse(fallback);
        log.info("Using environment variable {}={}", environmentVar, value);
        return value;
    }
}
