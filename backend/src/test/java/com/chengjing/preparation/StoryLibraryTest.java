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

/** 个人经历素材库：新增、编辑、搜索、删除，以及「本人确认的事实」与「模拟情境」的区分。 */
@SpringBootTest(classes = { com.chengjing.app.ChengjingApplication.class, StoryLibraryTest.SwitchableUserPort.class })
@AutoConfigureMockMvc
class StoryLibraryTest {
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
    void keepsOwnStorySeparateFromSimulatedScenario() throws Exception {
        String mine = createStory("对账链路重构", "我在结算系统里负责对账链路，约束是每晚必须出账。",
                "SELF", "后端工程师", "[\"结算\",\"重构\"]");
        String simulated = createStory("线上故障复盘", "假设我是值班负责人，先止损再定位根因。",
                "SCENARIO", "后端工程师", "[\"故障\"]");

        // 本人经历要本人核对后才算「确认的事实」，模拟情境一开始就不能算。
        mvc.perform(get("/api/v1/preparation/stories/{id}", mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("SELF"))
                .andExpect(jsonPath("$.data.confirmedByOwner").value(false))
                .andExpect(jsonPath("$.data.sourceLabel").value("本人经历（待核对）"));
        mvc.perform(patch("/api/v1/preparation/stories/{id}/confirmation", mine)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedByOwner").value(true))
                .andExpect(jsonPath("$.data.sourceLabel").value("本人确认的事实"));

        mvc.perform(get("/api/v1/preparation/stories/{id}", simulated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("SCENARIO"))
                .andExpect(jsonPath("$.data.sourceLabel").value("模拟情境（公开案例推演）"));
        mvc.perform(patch("/api/v1/preparation/stories/{id}/confirmation", simulated)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("模拟情境不能标记为本人确认的事实，请先把它改成本人真实经历"));
    }

    @Test
    void editsSearchAndDeletesOwnStories() throws Exception {
        String first = createStory("对账链路重构", "我在结算系统里负责对账链路，约束是每晚必须出账。",
                "SELF", "后端工程师", "[\"结算\"]");
        createStory("活动页改版", "我负责活动页改版，先和运营确认指标再动手。", "SELF", "前端工程师",
                "[\"改版\",\"指标\"]");

        mvc.perform(patch("/api/v1/preparation/stories/{id}", first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"对账链路重构（终版）\",\"tags\":[\"结算\",\"对账\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("对账链路重构（终版）"))
                .andExpect(jsonPath("$.data.content").value("我在结算系统里负责对账链路，约束是每晚必须出账。"))
                .andExpect(jsonPath("$.data.tags", hasSize(2)));

        // 搜索命中标题、内容、岗位与标签，且只看得到本人的素材。
        mvc.perform(get("/api/v1/preparation/stories").param("keyword", "对账"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(first));
        mvc.perform(get("/api/v1/preparation/stories").param("keyword", "前端"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("活动页改版"));
        mvc.perform(get("/api/v1/preparation/stories").param("keyword", "指标"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(get("/api/v1/preparation/stories").param("keyword", "不存在的关键词"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mvc.perform(delete("/api/v1/preparation/stories/{id}", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedId").value(first));
        mvc.perform(get("/api/v1/preparation/stories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void rejectsOtherAccountsAndInvalidInput() throws Exception {
        String mine = createStory("对账链路重构", "我在结算系统里负责对账链路，约束是每晚必须出账。",
                "SELF", "后端工程师", "[]");

        SwitchableUserPort.CURRENT.set("candidate-2");
        mvc.perform(get("/api/v1/preparation/stories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/preparation/stories/{id}", mine))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("这条经历素材属于其他账号，不能查看或修改"));
        mvc.perform(patch("/api/v1/preparation/stories/{id}", mine)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"偷看别人的素材\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/preparation/stories/{id}", mine))
                .andExpect(status().isForbidden());

        SwitchableUserPort.CURRENT.set("candidate-1");
        mvc.perform(post("/api/v1/preparation/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"缺来源\",\"content\":\"这条素材没有写来源。\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择素材来源：本人经历或模拟情境"));
        mvc.perform(post("/api/v1/preparation/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"来源写错\",\"content\":\"这条素材的来源不在允许范围内。\""
                                + ",\"source\":\"AI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("素材来源只能是 SELF（本人经历）或 SCENARIO（模拟情境）"));
        mvc.perform(post("/api/v1/preparation/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"太短\",\"content\":\"短\",\"source\":\"SELF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("素材内容需要 5～4000 字"));
        mvc.perform(get("/api/v1/preparation/stories/{id}", "missing-story"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("经历素材不存在"));
    }

    private String createStory(String title, String content, String source, String role, String tagsJson)
            throws Exception {
        String json = mvc.perform(post("/api/v1/preparation/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"" + content + "\""
                                + ",\"source\":\"" + source + "\",\"role\":\"" + role + "\""
                                + ",\"tags\":" + tagsJson + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        return idOf(json);
    }

    private static String idOf(String json) {
        String marker = "\"id\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("响应里没有素材 ID：" + json);
        }
        int from = start + marker.length();
        return json.substring(from, json.indexOf('"', from));
    }
}
