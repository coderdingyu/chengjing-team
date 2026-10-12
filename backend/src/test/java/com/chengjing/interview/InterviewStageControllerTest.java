package com.chengjing.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Owner: member C. C05 acceptance — the room shows where it is in the stage lineup and its expected
 * time, the draft survives moving on, and going over time is reported rather than enforced.
 */
@SpringBootTest(classes = com.chengjing.app.ChengjingApplication.class)
@AutoConfigureMockMvc
class InterviewStageControllerTest {

    private static final String USER = "user-c-stage01";

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

    private JsonNode newRoom(int minutes) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"后端工程师\",\"minutes\":" + minutes + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode nextStage(String id) throws Exception {
        return data(mvc.perform(post("/api/v1/interviews/{id}/stage/next", id)
                .header(RequestHeaderCurrentUserAdapter.HEADER, USER)).andReturn());
    }

    private int nextStageStatus(String id) throws Exception {
        return mvc.perform(post("/api/v1/interviews/{id}/stage/next", id)
                .header(RequestHeaderCurrentUserAdapter.HEADER, USER)).andReturn().getResponse().getStatus();
    }

    private JsonNode getRoom(String id) throws Exception {
        return data(mvc.perform(get("/api/v1/interviews/{id}", id)
                .header(RequestHeaderCurrentUserAdapter.HEADER, USER)).andReturn());
    }

    private String turnBody(String requestId, String questionId, String text) throws Exception {
        return json.writeValueAsString(new InterviewRoomService.TurnRequest(requestId, questionId, text));
    }

    @Test
    @DisplayName("房间展示环节阵容与当前进度，并给出本环节预计时长")
    void exposesStageLineupAndExpectedTime() throws Exception {
        JsonNode room = newRoom(15);
        String id = room.path("id").asText();

        JsonNode view = getRoom(id);
        JsonNode stages = view.path("stages");
        assertTrue(stages.size() >= 3, "至少有开场、项目、案例等环节");
        assertEquals(view.path("totalStages").asInt(), stages.size());
        assertEquals("开场", stages.get(0).asText(), "第一个环节是开场");

        assertEquals(0, view.path("stageIndex").asInt());
        assertEquals(15 * 60 / stages.size(), view.path("stageExpectedSeconds").asInt(),
                "预计时长按目标时长平均分配到各环节");
        assertTrue(view.path("stageElapsedSeconds").asLong() >= 0);
        assertEquals(false, view.path("isLastStage").asBoolean());
    }

    @Test
    @DisplayName("推进到下一环节会换题，并且不丢掉草稿")
    void advancingNextStageKeepsDraft() throws Exception {
        JsonNode room = newRoom(30);
        String id = room.path("id").asText();
        String firstQuestion = room.path("questionId").asText();

        // 先写一半草稿
        mvc.perform(put("/api/v1/interviews/{id}/draft", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InterviewRoomService.DraftRequest("写到一半的想法"))))
                .andExpect(status().isOk());

        JsonNode next = nextStage(id);
        assertEquals(1, next.path("stageIndex").asInt());
        assertEquals("项目深挖", next.path("stage").path("title").asText());
        assertTrue(!firstQuestion.equals(next.path("questionId").asText()), "换环节后题目标识随之变化");
        assertEquals("写到一半的想法", next.path("draft").asText(), "换环节不能丢掉草稿");

        // 新环节的计时从零开始
        assertEquals(0, next.path("stageElapsedSeconds").asLong());
    }

    @Test
    @DisplayName("最后一个环节不能再推进，但不会被打断或自动结束")
    void lastStageCannotAdvanceAndIsNotInterrupted() throws Exception {
        JsonNode room = newRoom(15);
        String id = room.path("id").asText();
        int total = room.path("totalStages").asInt();

        for (int i = 1; i < total; i++) {
            nextStage(id);
        }

        JsonNode last = getRoom(id);
        assertEquals(total - 1, last.path("stageIndex").asInt());
        assertEquals(true, last.path("isLastStage").asBoolean());

        // 到头了就不能再推进
        assertEquals(409, nextStageStatus(id));

        // 超时只提示，不强制中断：房间仍在进行中，并且还能继续作答
        assertTrue(last.path("stageElapsedSeconds").asLong() >= 0);
        assertTrue(last.path("stageExpectedSeconds").asLong() > 0);
        assertEquals("ACTIVE", last.path("status").asText(), "不会因为时间到了就自动结束");

        String answer = turnBody("req-stage-last1", last.path("questionId").asText(), "最后环节的回答");
        mvc.perform(post("/api/v1/interviews/{id}/turns", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answer))
                .andExpect(status().isOk());

        assertEquals("ACTIVE", getRoom(id).path("status").asText());
    }

    @Test
    @DisplayName("暂停时不能推进环节")
    void cannotAdvanceWhilePaused() throws Exception {
        JsonNode room = newRoom(15);
        String id = room.path("id").asText();

        mvc.perform(post("/api/v1/interviews/{id}/pause", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(status().isOk());

        assertEquals(409, nextStageStatus(id));
    }

    @Test
    @DisplayName("结束后不能再推进环节")
    void cannotAdvanceAfterFinish() throws Exception {
        JsonNode room = newRoom(15);
        String id = room.path("id").asText();

        mvc.perform(post("/api/v1/interviews/{id}/finish", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(status().isOk());

        assertEquals(409, nextStageStatus(id));
    }

    @Test
    @DisplayName("其余账号不能推进他人房间的环节")
    void refusesAdvanceOnForeignRoom() throws Exception {
        JsonNode room = newRoom(15);
        String id = room.path("id").asText();

        mvc.perform(post("/api/v1/interviews/{id}/stage/next", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, "user-c-other001"))
                .andExpect(status().isNotFound());
    }
}
