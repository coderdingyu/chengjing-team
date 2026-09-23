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

/** 单题专项练习：从深挖节点发起题目、保存文字回答、失败时保留草稿。 */
@SpringBootTest(classes = { com.chengjing.app.ChengjingApplication.class, PracticeTest.SwitchableUserPort.class })
@AutoConfigureMockMvc
class PracticeTest {
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
    void asksTheNodeQuestionAndKeepsEverySubmittedAnswer() throws Exception {
        String planId = createPlan("后端工程师");
        String nodeId = firstNodeId(planId);
        String practiceId = createPractice(planId, nodeId);

        // 题目来自该节点的提问，一开始没有回答，状态是草稿中。
        mvc.perform(get("/api/v1/preparation/practices/{id}", practiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(planId))
                .andExpect(jsonPath("$.data.nodeId").value(nodeId))
                .andExpect(jsonPath("$.data.nodeTitle").value("项目背景"))
                // 题目用节点里的提问原文，岗位场景会写进提问里。
                .andExpect(jsonPath("$.data.question")
                        .value(org.hamcrest.Matchers.containsString("一次线上故障排查或接口性能优化")))
                .andExpect(jsonPath("$.data.question")
                        .value(org.hamcrest.Matchers.containsString("只写可以公开的事实")))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.statusLabel").value("草稿中"))
                .andExpect(jsonPath("$.data.attemptCount").value(0));

        submit(practiceId, "我在结算系统里负责对账链路，约束是每晚必须出账。", "req-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.attemptCount").value(1))
                .andExpect(jsonPath("$.data.answers[0].revision").value(1))
                .andExpect(jsonPath("$.data.answers[0].text")
                        .value("我在结算系统里负责对账链路，约束是每晚必须出账。"));

        // 同一道题可以重答，历次回答按顺序留着，便于对照自己前后两次的说法。
        submit(practiceId, "我负责对账链路，先把口径写进设计文档再改代码。", "req-2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptCount").value(2))
                .andExpect(jsonPath("$.data.answers", hasSize(2)))
                .andExpect(jsonPath("$.data.answers[1].revision").value(2))
                .andExpect(jsonPath("$.data.answers[0].text")
                        .value("我在结算系统里负责对账链路，约束是每晚必须出账。"));
    }

    @Test
    void keepsTheDraftWhenSubmittingFails() throws Exception {
        String planId = createPlan("前端工程师");
        String practiceId = createPractice(planId, firstNodeId(planId));

        mvc.perform(patch("/api/v1/preparation/practices/{id}", practiceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draft\":\"先写活动页改版的背景：指标由运营确认，我负责前端。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft")
                        .value("先写活动页改版的背景：指标由运营确认，我负责前端。"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        // 提交太短被拒绝：草稿一个字都不能丢，本人接着改就行。
        submit(practiceId, "短", "req-fail")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("回答需要 2～4000 字，写清你本人做过的事"));
        mvc.perform(get("/api/v1/preparation/practices/{id}", practiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft")
                        .value("先写活动页改版的背景：指标由运营确认，我负责前端。"))
                .andExpect(jsonPath("$.data.attemptCount").value(0));

        // 草稿本身也限制长度，超长时明确报错而不是静默截断。
        mvc.perform(patch("/api/v1/preparation/practices/{id}", practiceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draft\":\"" + "长".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("草稿最多 4000 字，先记下要点也可以"));

        // 改好草稿后提交成功，草稿随之清空，回答留下。
        mvc.perform(patch("/api/v1/preparation/practices/{id}", practiceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draft\":\"我负责活动页改版的前端实现，先和运营确认指标再动手。\"}"))
                .andExpect(status().isOk());
        submit(practiceId, "我负责活动页改版的前端实现，先和运营确认指标再动手。", "req-ok")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.draft").value(""))
                .andExpect(jsonPath("$.data.attemptCount").value(1));
    }

    @Test
    void repeatedRequestIdDoesNotCreateASecondAnswer() throws Exception {
        String planId = createPlan("产品经理");
        String practiceId = createPractice(planId, firstNodeId(planId));
        String text = "我负责版本节奏，先砍掉两个非核心需求保住上线时间。";

        submit(practiceId, text, "req-same").andExpect(status().isCreated());
        submit(practiceId, text, "req-same")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptCount").value(1));

        // 同一个提交标识配另一份回答是冲突，避免把两次不同的回答混成一条。
        submit(practiceId, "我负责版本节奏，这次换成先补齐测试环境。", "req-same")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("这次提交已经保存了另一份回答，请刷新查看后再重新提交"));
    }

    @Test
    void reopeningTheSameNodeReusesThePracticeAndKeepsItsDraft() throws Exception {
        String planId = createPlan("数据分析师");
        String nodeId = firstNodeId(planId);
        String practiceId = createPractice(planId, nodeId);
        mvc.perform(patch("/api/v1/preparation/practices/{id}", practiceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draft\":\"先写指标口径统一的过程。\"}"))
                .andExpect(status().isOk());

        // 再次从同一个节点进入：还是同一条练习，草稿原样还在。
        String again = createPractice(planId, nodeId);
        org.junit.jupiter.api.Assertions.assertEquals(practiceId, again);
        mvc.perform(get("/api/v1/preparation/practices/{id}", again))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft").value("先写指标口径统一的过程。"));
        mvc.perform(get("/api/v1/preparation/practices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void rejectsOtherAccountsAndMissingNodes() throws Exception {
        String planId = createPlan("测试工程师");
        String nodeId = firstNodeId(planId);
        String practiceId = createPractice(planId, nodeId);
        submit(practiceId, "我负责回归漏测的复盘，把用例补到门禁里。", "req-1").andExpect(status().isCreated());

        SwitchableUserPort.CURRENT.set("candidate-2");
        mvc.perform(get("/api/v1/preparation/practices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/preparation/practices/{id}", practiceId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("这条练习记录属于其他账号，不能查看或修改"));
        mvc.perform(patch("/api/v1/preparation/practices/{id}", practiceId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draft\":\"看别人的草稿\"}"))
                .andExpect(status().isForbidden());
        submit(practiceId, "替别人提交回答", "req-2").andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/preparation/practices/{id}", practiceId))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/preparation/plans/{plan}/nodes/{node}/practices", planId, nodeId))
                .andExpect(status().isForbidden());

        SwitchableUserPort.CURRENT.set("candidate-1");
        mvc.perform(post("/api/v1/preparation/plans/{plan}/nodes/{node}/practices", planId, "missing-node"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("深挖节点不存在，请先在深挖地图里生成节点"));
        mvc.perform(get("/api/v1/preparation/practices/{id}", "missing-practice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("练习记录不存在"));

        // 删除只影响自己这一条练习，列表随之减少。
        mvc.perform(get("/api/v1/preparation/practices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(delete("/api/v1/preparation/practices/{id}", practiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedId").value(practiceId));
        mvc.perform(get("/api/v1/preparation/practices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    private org.springframework.test.web.servlet.ResultActions submit(String practiceId, String text,
            String requestId) throws Exception {
        return mvc.perform(post("/api/v1/preparation/practices/{id}/answers", practiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\",\"requestId\":\"" + requestId + "\"}"));
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

    private String firstNodeId(String planId) throws Exception {
        String json = mvc.perform(post("/api/v1/preparation/plans/{id}/nodes", planId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return idOf(json, "\"node\":{\"id\":\"");
    }

    private String createPractice(String planId, String nodeId) throws Exception {
        String json = mvc.perform(post("/api/v1/preparation/plans/{plan}/nodes/{node}/practices", planId, nodeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        return idOf(json, "\"id\":\"");
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
