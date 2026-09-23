package com.chengjing.preparation;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = com.chengjing.app.ChengjingApplication.class)
@AutoConfigureMockMvc
class ScenarioControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ScenarioCatalog catalog;

    @Test void elevenDistinctRolesHaveUsableSimulatedCases() throws Exception {
        mvc.perform(get("/api/v1/preparation/roles")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data", hasSize(11)));
        assertEquals(11, catalog.list(null).stream().map(ScenarioCatalog.Scenario::id).distinct().count());
        for (var role : catalog.roles()) {
            var scenarios = catalog.list(role);
            assertFalse(scenarios.isEmpty());
            for (var s : scenarios) {
                assertTrue(s.simulated());
                assertFalse(s.situation().isBlank());
                assertFalse(s.goal().isBlank());
                mvc.perform(get("/api/v1/preparation/scenarios/{id}", s.id()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value(role));
            }
        }
    }

    @Test void filtersRolesAndHandlesMissingCases() throws Exception {
        mvc.perform(get("/api/v1/preparation/scenarios").param("role", "前端工程师"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].id").value("frontend-checkout"));
        mvc.perform(get("/api/v1/preparation/scenarios").param("role", "未知岗位"))
            .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/preparation/scenarios/missing"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("岗位情境不存在"));
    }
}
