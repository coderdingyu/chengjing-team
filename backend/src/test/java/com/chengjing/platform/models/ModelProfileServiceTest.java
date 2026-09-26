package com.chengjing.platform.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengjing.platform.PlatformException;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ModelProfileServiceTest {
    @Test
    void encryptsKeysAndKeepsProfilesScopedToOwner() throws Exception {
        var folder = Files.createTempDirectory("chengjing-models-");
        var ds = new DriverManagerDataSource("jdbc:h2:file:" + folder.resolve("db").toString().replace('\\', '/'), "sa", "");
        Flyway.configure().dataSource(ds).load().migrate();
        var jdbc = new JdbcTemplate(ds);
        var cipher = new SecretCipher(folder.resolve("master.key").toString());
        var profiles = new ModelProfileService(jdbc, cipher, new EndpointPolicy(false));
        var input = new ModelProfileService.Input("面试模型", "stepfun", "https://api.stepfun.com/v1", "step-5-preview", "test-secret-123");
        var saved = profiles.save("person-a", null, input);

        assertThat(saved.hasKey()).isTrue();
        assertThat(profiles.settings("person-a").profiles()).hasSize(1);
        assertThat(profiles.settings("person-b").profiles()).isEmpty();
        assertThat(profiles.settings("person-a").toString()).doesNotContain("test-secret-123");
        assertThat(jdbc.queryForObject("SELECT secret_cipher FROM model_profiles WHERE id=?", String.class, saved.id()))
                .doesNotContain("test-secret-123").startsWith("v1:");
        assertThatThrownBy(() -> profiles.route("person-b", "dialogue", saved.id())).isInstanceOf(PlatformException.class);
        profiles.route("person-a", "dialogue", saved.id());
        assertThat(profiles.resolve("person-a", "dialogue").key).isEqualTo("test-secret-123");

        profiles.save("person-a", saved.id(), new ModelProfileService.Input("新名称", "stepfun", input.baseUrl(), input.model(), ""));
        assertThat(profiles.resolve("person-a", "dialogue").key).isEqualTo("test-secret-123");
        var restarted = new ModelProfileService(jdbc, new SecretCipher(folder.resolve("master.key").toString()), new EndpointPolicy(false));
        assertThat(restarted.resolve("person-a", "dialogue").key).isEqualTo("test-secret-123");
        restarted.delete("person-a", saved.id());
        assertThat(restarted.settings("person-a").routes()).isEmpty();
    }

    @Test
    void rejectsUnsafeEndpointsAndKeyReuseAcrossHosts() throws Exception {
        var folder = Files.createTempDirectory("chengjing-model-policy-");
        var ds = new DriverManagerDataSource("jdbc:h2:mem:model-policy;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(ds).load().migrate();
        var service = new ModelProfileService(new JdbcTemplate(ds), new SecretCipher(folder.resolve("key").toString()), new EndpointPolicy(false));
        assertThatThrownBy(() -> service.save("owner", null,
                new ModelProfileService.Input("x", "custom", "http://127.0.0.1:8080/v1", "model", "key")))
                .isInstanceOf(PlatformException.class);
        var saved = service.save("owner", null,
                new ModelProfileService.Input("x", "custom", "https://example.com/v1", "model", "key"));
        assertThatThrownBy(() -> service.save("owner", saved.id(),
                new ModelProfileService.Input("x", "custom", "https://other.example/v1", "model", "")))
                .isInstanceOf(PlatformException.class);
    }
}
