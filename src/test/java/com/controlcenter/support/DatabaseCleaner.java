package com.controlcenter.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resets the shared in-memory database between integration tests. */
@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        jdbc.execute("DELETE FROM deployments");
        jdbc.execute("DELETE FROM environments");
        jdbc.execute("DELETE FROM applications");
    }
}
