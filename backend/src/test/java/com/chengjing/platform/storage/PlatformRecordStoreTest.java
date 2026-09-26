package com.chengjing.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PlatformRecordStoreTest {
    @Test
    void recordsSurviveRestartAndMigrationIsRepeatable() throws Exception {
        var file = Files.createTempDirectory("chengjing-store-").resolve("app");
        var url = "jdbc:h2:file:" + file.toAbsolutePath().toString().replace('\\', '/') + ";DB_CLOSE_ON_EXIT=FALSE";
        var dataSource = new DriverManagerDataSource(url, "sa", "");
        var flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        var mapper = new ObjectMapper();
        var first = new PlatformRecordStore(new JdbcTemplate(dataSource), mapper);
        for (var kind : new String[]{"account", "preparation", "interview", "assessment"})
            first.put(kind, "candidate-1", "entry-1", mapper.createObjectNode().put("kind", kind));
        first.put("interview", "candidate-2", "entry-1", mapper.createObjectNode().put("private", true));

        var restarted = new DriverManagerDataSource(url, "sa", "");
        assertThat(Flyway.configure().dataSource(restarted).load().migrate().migrationsExecuted).isZero();
        var second = new PlatformRecordStore(new JdbcTemplate(restarted), mapper);
        for (var kind : new String[]{"account", "preparation", "interview", "assessment"}) {
            assertThat(second.find(kind, "candidate-1", "entry-1")).isPresent();
            assertThat(second.list(kind, "candidate-1")).hasSize(1);
        }
        assertThat(second.find("interview", "candidate-1", "entry-2")).isEmpty();
        assertThat(second.list("interview", "candidate-2")).hasSize(1);
        assertThat(second.deleteOwner("candidate-1")).isEqualTo(4);
        assertThat(second.list("interview", "candidate-2")).hasSize(1);
    }
}
