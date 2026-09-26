package com.chengjing.platform.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengjing.platform.PlatformException;
import com.chengjing.platform.models.EndpointPolicy;
import com.chengjing.platform.models.ModelProfileService;
import com.chengjing.platform.models.SecretCipher;
import com.chengjing.platform.storage.PlatformRecordStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LiveTicketServiceTest {
    @Test
    void ticketIsOneUseAndTranscriptSurvivesReconnectWithoutAudio() throws Exception {
        var folder = Files.createTempDirectory("chengjing-live-");
        var ds = new DriverManagerDataSource("jdbc:h2:file:" + folder.resolve("db").toString().replace('\\', '/'), "sa", "");
        Flyway.configure().dataSource(ds).load().migrate();
        var jdbc = new JdbcTemplate(ds);
        var mapper = new ObjectMapper();
        var records = new PlatformRecordStore(jdbc, mapper);
        var profiles = new ModelProfileService(jdbc, new SecretCipher(folder.resolve("key").toString()), new EndpointPolicy(false));
        var profile = profiles.save("owner", null, new ModelProfileService.Input("Live", "stepfun",
                "https://api.stepfun.com/v1", "stepaudio-3-realtime-preview", "private-key"));
        profiles.route("owner", "live", profile.id());
        var tickets = new LiveTicketService(profiles, records, mapper);
        var issued = tickets.issue("owner", "产品经理", null);
        assertThat(issued.toString()).doesNotContain("private-key");
        var ticket = tickets.consume(issued.ticket());
        assertThatThrownBy(() -> tickets.consume(issued.ticket())).isInstanceOf(PlatformException.class);
        tickets.append(ticket, "interviewer", "请介绍一个项目");
        tickets.append(ticket, "candidate", "我负责了需求拆解");
        assertThat(tickets.transcript("owner", issued.sessionId()).path("events")).hasSize(2);
        assertThatThrownBy(() -> tickets.transcript("stranger", issued.sessionId())).isInstanceOf(PlatformException.class);
        var restarted = new LiveTicketService(profiles, new PlatformRecordStore(new JdbcTemplate(ds), mapper), mapper);
        assertThat(restarted.issue("owner", "产品经理", issued.sessionId()).sessionId()).isEqualTo(issued.sessionId());
        assertThat(restarted.transcript("owner", issued.sessionId()).toString()).contains("需求拆解").doesNotContain("private-key");
    }
}
