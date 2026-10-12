package com.chengjing.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Owner: member C. C04 acceptance — a draft survives a refresh, pausing stops the clock and blocks
 * answering, and finishing ends the room.
 */
@SpringBootTest(classes = com.chengjing.app.ChengjingApplication.class)
@AutoConfigureMockMvc
class InterviewSessionControllerTest {

    private static final String USER = "user-c-session1";

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

    private JsonNode newRoom() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/interviews")
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"数据分析师\",\"minutes\":30}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode getRoom(String id) throws Exception {
        return data(mvc.perform(get("/api/v1/interviews/{id}", id)
                .header(RequestHeaderCurrentUserAdapter.HEADER, USER)).andReturn());
    }

    private JsonNode postRoom(String id, String suffix) throws Exception {
        return data(mvc.perform(post("/api/v1/interviews/{id}/{suffix}", id, suffix)
                .header(RequestHeaderCurrentUserAdapter.HEADER, USER)).andReturn());
    }

    private int postStatus(String id, String suffix, String body) throws Exception {
        var request = post("/api/v1/interviews/{id}/{suffix}", id, suffix)
                .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(body);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private int putDraftStatus(String id, String text) throws Exception {
        return mvc.perform(put("/api/v1/interviews/{id}/draft", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InterviewRoomService.DraftRequest(text))))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode putDraft(String id, String text) throws Exception {
        return data(mvc.perform(put("/api/v1/interviews/{id}/draft", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InterviewRoomService.DraftRequest(text))))
                .andReturn());
    }

    private String turnBody(String requestId, String questionId, String text) throws Exception {
        return json.writeValueAsString(new InterviewRoomService.TurnRequest(requestId, questionId, text));
    }

    @Test
    @DisplayName("草稿自动保存，刷新后可以恢复")
    void draftSurvivesRefresh() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();

        assertEquals(200, putDraftStatus(id, "我先说结论，再举一个库存周转的例子"));

        // 刷新：重新打开房间时草稿还在
        mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(jsonPath("$.data.draft").value("我先说结论，再举一个库存周转的例子"));
    }

    @Test
    @DisplayName("提交回答后草稿被清空，不会在下次刷新时重现")
    void draftIsClearedAfterAnswer() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String questionId = room.path("questionId").asText();

        putDraft(id, "临时草稿");
        assertEquals(200, postStatus(id, "turns", turnBody("req-draft-0001", questionId, "正式回答")));

        mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(jsonPath("$.data.draft").value(""));
    }

    @Test
    @DisplayName("暂停后停止计时，并且不能再提交回答")
    void pauseStopsClockAndBlocksAnswers() throws Exception {
        String activeId = newRoom().path("id").asText();

        JsonNode pausedRoom = newRoom();
        String pausedId = pausedRoom.path("id").asText();
        String pausedQuestion = pausedRoom.path("questionId").asText();

        assertEquals(200, postStatus(pausedId, "pause", null));

        Thread.sleep(1200);

        JsonNode paused = getRoom(pausedId);
        JsonNode active = getRoom(activeId);
        assertEquals("PAUSED", paused.path("status").asText());
        assertEquals(0, paused.path("elapsedSeconds").asLong(), "暂停后计时不再增长");
        assertTrue(active.path("elapsedSeconds").asLong() >= 1, "未暂停的房间计时继续");

        // 暂停期间不接受回答
        String answer = turnBody("req-pause-0001", pausedQuestion, "暂停时写的回答");
        assertEquals(409, postStatus(pausedId, "turns", answer));

        // 继续后恢复受理
        assertEquals(200, postStatus(pausedId, "resume", null));
        assertEquals(200, postStatus(pausedId, "turns", answer));
        assertEquals(1, getRoom(pausedId).path("answers").size());
    }

    @Test
    @DisplayName("暂停时可以保存草稿，状态保持在暂停")
    void draftCanBeSavedWhilePaused() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();

        postRoom(id, "pause");
        assertEquals(200, putDraftStatus(id, "暂停时想到的要点"));

        JsonNode paused = getRoom(id);
        assertEquals("PAUSED", paused.path("status").asText());
        assertEquals("暂停时想到的要点", paused.path("draft").asText());
    }

    @Test
    @DisplayName("结束后面试是终态：计时冻结、不能再作答或改草稿")
    void finishIsTerminal() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String questionId = room.path("questionId").asText();

        putDraft(id, "未提交草稿");

        JsonNode finished = postRoom(id, "finish");
        assertEquals("COMPLETED", finished.path("status").asText());
        assertFalse(finished.path("endedAt").isNull(), "结束时间要写下来供复盘页使用");

        long frozen = finished.path("elapsedSeconds").asLong();
        Thread.sleep(1100);
        assertEquals(frozen, getRoom(id).path("elapsedSeconds").asLong(), "结束后计时不再增长");

        assertEquals(409, postStatus(id, "turns", turnBody("req-finish-001", questionId, "结束后的回答")));
        assertEquals(409, putDraftStatus(id, "结束后的草稿"));
    }

    @Test
    @DisplayName("其余账号不能暂停、继续、结束或写草稿")
    void refusesControlOfForeignRoom() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String foreign = "user-c-other001";

        for (String suffix : new String[] {"pause", "resume", "finish"}) {
            mvc.perform(post("/api/v1/interviews/{id}/{suffix}", id, suffix)
                            .header(RequestHeaderCurrentUserAdapter.HEADER, foreign))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/v1/interviews/{id}/draft", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, foreign)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InterviewRoomService.DraftRequest("外人的草稿"))))
                .andExpect(status().isNotFound());
    }
}
