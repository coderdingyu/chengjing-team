package com.chengjing.preparation;

import static org.hamcrest.Matchers.hasSize;
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

/** 简历深挖节点：生成四类节点、分别保存状态与内容、归属隔离与字段校验。 */
@SpringBootTest(classes = { com.chengjing.app.ChengjingApplication.class, PlanNodeTest.SwitchableUserPort.class })
@AutoConfigureMockMvc
class PlanNodeTest {
    @Autowired MockMvc mvc;
    @Autowired PreparationStore store;

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
    void generatesBackgroundActionTradeoffAndResultNodes() throws Exception {
        String planId = createPlan("后端工程师");

        mvc.perform(post("/api/v1/preparation/plans/{id}/nodes", planId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"focus\":\"支付对账重构\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].node.kind").value("BACKGROUND"))
                .andExpect(jsonPath("$.data[0].node.title").value("项目背景"))
                .andExpect(jsonPath("$.data[0].node.status").value("TODO"))
                .andExpect(jsonPath("$.data[0].node.prompt").value(org.hamcrest.Matchers.containsString("支付对账重构")))
                .andExpect(jsonPath("$.data[1].node.kind").value("ACTION"))
                .andExpect(jsonPath("$.data[2].node.kind").value("TRADEOFF"))
                .andExpect(jsonPath("$.data[3].node.kind").value("RESULT"))
                .andExpect(jsonPath("$.data[3].planId").value(planId))
                .andExpect(jsonPath("$.data[3].role").value("后端工程师"));

        mvc.perform(get("/api/v1/preparation/plans/{id}/nodes", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)));
    }

    @Test
    void keepsEveryNodeStatusAndContentSeparately() throws Exception {
        String planId = createPlan("前端工程师");
        String nodesJson = generate(planId);
        String first = nodeIdAt(nodesJson, 0);
        String second = nodeIdAt(nodesJson, 1);

        // 只写内容：节点自然进入「草稿中」，不会越过本人直接算完成。
        mvc.perform(patch("/api/v1/preparation/plans/{id}/nodes/{node}", planId, first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"我在结算系统里负责对账链路，约束是每晚必须出账。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.node.status").value("DRAFTING"))
                .andExpect(jsonPath("$.data.node.content").value("我在结算系统里负责对账链路，约束是每晚必须出账。"));

        mvc.perform(patch("/api/v1/preparation/plans/{id}/nodes/{node}", planId, second)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.node.status").value("DONE"));

        // 第二个节点的改动不能覆盖第一个节点。
        mvc.perform(get("/api/v1/preparation/plans/{id}/nodes", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].node.status").value("DRAFTING"))
                .andExpect(jsonPath("$.data[0].node.content")
                        .value("我在结算系统里负责对账链路，约束是每晚必须出账。"))
                .andExpect(jsonPath("$.data[1].node.status").value("DONE"))
                .andExpect(jsonPath("$.data[2].node.status").value("TODO"));
    }

    @Test
    void regeneratingKeepsWrittenNodes() throws Exception {
        String planId = createPlan("产品经理");
        String nodesJson = generate(planId);
        String first = nodeIdAt(nodesJson, 0);
        mvc.perform(patch("/api/v1/preparation/plans/{id}/nodes/{node}", planId, first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"版本节奏由我排。\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/preparation/plans/{id}/nodes", planId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].node.id").value(first))
                .andExpect(jsonPath("$.data[0].node.content").value("版本节奏由我排。"));

        // replace=true 是本人主动重做，节点重新回到待写状态。
        mvc.perform(post("/api/v1/preparation/plans/{id}/nodes", planId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"replace\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].node.content").value(""))
                .andExpect(jsonPath("$.data[0].node.status").value("TODO"));
    }

    @Test
    void rejectsOtherAccountsAndInvalidInput() throws Exception {
        String planId = createPlan("测试工程师");
        String nodesJson = generate(planId);
        String first = nodeIdAt(nodesJson, 0);

        SwitchableUserPort.CURRENT.set("candidate-2");
        mvc.perform(get("/api/v1/preparation/plans/{id}/nodes", planId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("这条准备计划属于其他账号，不能查看或修改"));
        mvc.perform(post("/api/v1/preparation/plans/{id}/nodes", planId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/preparation/plans/{id}/nodes/{node}", planId, first)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());

        SwitchableUserPort.CURRENT.set("candidate-1");
        mvc.perform(patch("/api/v1/preparation/plans/{id}/nodes/{node}", planId, first)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("节点状态只能是 TODO、DRAFTING 或 DONE"));
        mvc.perform(patch("/api/v1/preparation/plans/{id}/nodes/{node}", planId, "missing-node")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("深挖节点不存在"));
        mvc.perform(get("/api/v1/preparation/plans/{id}/nodes", "missing-plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("准备计划不存在"));
    }

    private String createPlan(String role) throws Exception {
        String json = mvc.perform(post("/api/v1/preparation/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\",\"title\":\"秋招准备\""
                                + ",\"goal\":\"把一次真实经历讲出取舍与验证\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(json, "\"id\":\"");
    }

    private String generate(String planId) throws Exception {
        return mvc.perform(post("/api/v1/preparation/plans/{id}/nodes", planId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String nodeIdAt(String json, int index) {
        String marker = "\"node\":{\"id\":\"";
        int from = 0;
        for (int i = 0; i <= index; i++) {
            int start = json.indexOf(marker, from);
            if (start < 0) {
                throw new IllegalStateException("响应里没有第 " + index + " 个节点：" + json);
            }
            from = start + marker.length();
        }
        return json.substring(from, json.indexOf('"', from));
    }

    private static String idOf(String json, String marker) {
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("响应里没有目标 ID：" + json);
        }
        int from = start + marker.length();
        return json.substring(from, json.indexOf('"', from));
    }
}
