package com.chengjing.interview;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Owner: member C. C01 acceptance — create a room from role + duration, reopen the caller's own room
 * after a refresh, and refuse a room that belongs to somebody else.
 *
 * The boot class is named explicitly: this test lives in the interview package, which is a sibling
 * of {@code com.chengjing.app}, so the default "search upwards" rule would not find it.
 */
@SpringBootTest(classes = com.chengjing.app.ChengjingApplication.class)
@AutoConfigureMockMvc
class InterviewRoomControllerTest {

    private static final String USER_A = "user-a-0001";
    private static final String USER_B = "user-b-0002";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    InterviewRoomStore store;

    @BeforeEach
    void resetRooms() {
        store.clear();
    }

    private String createRoom(String userId, String body) throws Exception {
        String response = mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response).path("data");
        return node.path("id").asText();
    }

    @Test
    @DisplayName("创建房间：按岗位与时长生成完整房间，并给出首个环节")
    void createsRoomFromRoleAndDuration() throws Exception {
        mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"后端工程师\",\"level\":\"中级\",\"minutes\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("后端工程师"))
                .andExpect(jsonPath("$.data.level").value("中级"))
                .andExpect(jsonPath("$.data.minutes").value(30))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.stageIndex").value(0))
                .andExpect(jsonPath("$.data.totalStages").value(5))
                .andExpect(jsonPath("$.data.stage.title").value("开场"))
                .andExpect(jsonPath("$.data.stage.prompt").isNotEmpty());
    }

    @Test
    @DisplayName("时长决定环节数量：15、30、45 分钟得到不同长度的流程")
    void durationDrivesStageCount() throws Exception {
        mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"产品经理\",\"minutes\":15}"))
                .andExpect(jsonPath("$.data.totalStages").value(4));
        mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"产品经理\",\"minutes\":30}"))
                .andExpect(jsonPath("$.data.totalStages").value(5));
        mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"产品经理\",\"minutes\":45}"))
                .andExpect(jsonPath("$.data.totalStages").value(6));
    }

    @Test
    @DisplayName("刷新后可继续本人房间")
    void reopensOwnRoomAfterRefresh() throws Exception {
        String id = createRoom(USER_A, "{\"role\":\"前端工程师\",\"minutes\":15}");
        mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("陌生房间被拒绝：他人房间按不存在处理")
    void refusesRoomOwnedByAnotherUser() throws Exception {
        String id = createRoom(USER_A, "{\"role\":\"运营\",\"minutes\":15}");
        mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("不存在的房间返回 404")
    void refusesUnknownRoom() throws Exception {
        mvc.perform(get("/api/v1/interviews/{id}", "no-such-room")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("列表只显示本人房间")
    void listsOnlyOwnRooms() throws Exception {
        createRoom(USER_A, "{\"role\":\"运营\",\"minutes\":15}");
        createRoom(USER_A, "{\"role\":\"数据分析师\",\"minutes\":30}");
        createRoom(USER_B, "{\"role\":\"测试工程师\",\"minutes\":15}");
        mvc.perform(get("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_B))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("岗位与时长不合法时返回明确错误")
    void rejectsUnsupportedOptions() throws Exception {
        mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"不存在的岗位\",\"minutes\":15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择支持的岗位"));
        mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"运营\",\"minutes\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择 15、30 或 45 分钟"));
    }

    @Test
    @DisplayName("缺少用户标识时返回 401")
    void requiresOwnerKey() throws Exception {
        mvc.perform(get("/api/v1/interviews"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("岗位与时长选项来自服务端，前端不再重复维护一份列表")
    void exposesRoomOptions() throws Exception {
        mvc.perform(get("/api/v1/interviews/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roles.length()").value(11))
                .andExpect(jsonPath("$.data.levels[1]").value("中级"))
                .andExpect(jsonPath("$.data.durations.length()").value(3));
    }
}
