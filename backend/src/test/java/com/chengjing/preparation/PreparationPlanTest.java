package com.chengjing.preparation;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 岗位准备计划：创建、归属隔离、字段校验、状态流转与删除。 */
@SpringBootTest(classes = { com.chengjing.app.ChengjingApplication.class, PreparationPlanTest.SwitchableUserPort.class })
@AutoConfigureMockMvc
class PreparationPlanTest {
    @Autowired MockMvc mvc;
    @Autowired PreparationStore store;

    /** A02 身份模块接入前，用可切换账号的端口验证归属隔离。 */
    @TestConfiguration
    static class SwitchableUserPort {
        static final ThreadLocal<String> CURRENT = ThreadLocal.withInitial(() -> "candidate-1");

        @Bean
        @Primary
        PreparationUserPort preparationUserPort() {
            return CURRENT::get;
        }
    }

    @BeforeEach
    void signInAsCandidateOne() {
        SwitchableUserPort.CURRENT.set("candidate-1");
        PreparationTestStore.reset(store);
    }

    @AfterEach
    void restoreDefaultAccount() {
        SwitchableUserPort.CURRENT.set("candidate-1");
    }

    @Test
    void createdPlanBelongsToItsOwnerOnly() throws Exception {
        String planId = createPlan("后端工程师", "秋招后端准备", "backend-incident");

        mvc.perform(get("/api/v1/preparation/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].scenarioId").value("backend-incident"))
                .andExpect(jsonPath("$.data[0].status").value("PLANNING"));

        SwitchableUserPort.CURRENT.set("candidate-2");
        mvc.perform(get("/api/v1/preparation/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/preparation/plans/{id}", planId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("这条准备计划属于其他账号，不能查看或修改"));
        mvc.perform(patch("/api/v1/preparation/plans/{id}", planId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"偷改别人的计划\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/preparation/plans/{id}", planId))
                .andExpect(status().isForbidden());

        SwitchableUserPort.CURRENT.set("candidate-1");
        mvc.perform(get("/api/v1/preparation/plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("秋招后端准备"));
    }

    @Test
    void validatesFieldsAndKeepsScenarioReference() throws Exception {
        mvc.perform(post("/api/v1/preparation/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body("前端工程师", "短", "frontend-checkout")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("计划名称需要 2～60 字"));
        mvc.perform(post("/api/v1/preparation/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"前端工程师\",\"title\":\"秋招前端准备\",\"goal\":\"讲清取舍\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("准备目标需要 5～200 字，写清这次准备要解决什么"));
        mvc.perform(get("/api/v1/preparation/plans/missing-plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("准备计划不存在"));

        String json = mvc.perform(post("/api/v1/preparation/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(body("前端工程师", "秋招前端准备", "frontend-checkout")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scenarioId").value("frontend-checkout"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/preparation/plans/{id}", idOf(json)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenarioId").value("frontend-checkout"))
                .andExpect(jsonPath("$.data.role").value("前端工程师"));
    }

    @Test
    void updatesStatusWithoutLosingTheRole() throws Exception {
        String planId = createPlan("后端工程师", "秋招后端准备", "backend-incident");

        mvc.perform(patch("/api/v1/preparation/plans/{id}", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"后端二面准备\",\"status\":\"ready\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("后端二面准备"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.role").value("后端工程师"));

        mvc.perform(patch("/api/v1/preparation/plans/{id}", planId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("计划状态只能是 PLANNING、READY 或 ARCHIVED"));
    }

    @Test
    void deletesOnlyOwnPlan() throws Exception {
        String planId = createPlan("测试工程师", "测试岗准备", "qa-release");

        mvc.perform(delete("/api/v1/preparation/plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedId").value(planId));
        mvc.perform(get("/api/v1/preparation/plans/{id}", planId))
                .andExpect(status().isNotFound());
    }

    private String createPlan(String role, String title, String scenarioId) throws Exception {
        String json = mvc.perform(post("/api/v1/preparation/plans")
                        .contentType(MediaType.APPLICATION_JSON).content(body(role, title, scenarioId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        return idOf(json);
    }

    private static String body(String role, String title, String scenarioId) {
        return "{\"role\":\"" + role + "\",\"title\":\"" + title
                + "\",\"goal\":\"在四十分钟里讲清一次真实的取舍和验证\""
                + ",\"scenarioId\":\"" + scenarioId + "\"}";
    }

    private static String idOf(String json) {
        String marker = "\"id\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("响应里没有计划 ID：" + json);
        }
        int from = start + marker.length();
        return json.substring(from, json.indexOf('\"', from));
    }
}
