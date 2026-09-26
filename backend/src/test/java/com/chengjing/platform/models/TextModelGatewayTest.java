package com.chengjing.platform.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengjing.platform.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TextModelGatewayTest {
    @Test
    void sendsSelectedModelAndRejectsInvalidOutputWithoutLeakingKey() throws Exception {
        var captured = new AtomicReference<String>();
        var response = new AtomicReference<>("{\"choices\":[{\"message\":{\"content\":\"{\\\"connected\\\":true}\"}}]}");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            var folder = Files.createTempDirectory("chengjing-gateway-");
            var ds = new DriverManagerDataSource("jdbc:h2:mem:gateway-test;DB_CLOSE_DELAY=-1", "sa", "");
            Flyway.configure().dataSource(ds).load().migrate();
            var policy = new EndpointPolicy(true);
            var profiles = new ModelProfileService(new JdbcTemplate(ds), new SecretCipher(folder.resolve("key").toString()), policy);
            var view = profiles.save("person-a", null, new ModelProfileService.Input("local", "custom",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-model", "private-test-key"));
            profiles.route("person-a", "dialogue", view.id());
            var gateway = new TextModelGateway(profiles, policy, new ObjectMapper(), 10);
            assertThat(gateway.completeJson("person-a", "dialogue", "Return JSON.", "hello").content().path("connected").asBoolean()).isTrue();
            assertThat(captured.get()).contains("test-model").doesNotContain("private-test-key");
            response.set("{\"choices\":[{\"message\":{\"content\":\"not json\"}}]}");
            assertThatThrownBy(() -> gateway.completeJson("person-a", "dialogue", "Return JSON.", "hello"))
                    .isInstanceOf(PlatformException.class).hasMessageNotContaining("private-test-key");
        } finally { server.stop(0); }
    }
}
