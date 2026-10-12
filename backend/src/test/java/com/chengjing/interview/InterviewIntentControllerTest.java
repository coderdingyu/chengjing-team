package com.chengjing.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Owner: member C. C03 acceptance — clarification / replay / thinking each get a clear response, and
 * clarification and replay are never counted as a scored answer.
 */
@SpringBootTest(classes = com.chengjing.app.ChengjingApplication.class)
@AutoConfigureMockMvc
class InterviewIntentControllerTest {

    private static final String USER = "user-c-intent01";

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
                        .content("{\"role\":\"后端工程师\",\"minutes\":15}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MvcResult request(String roomId, String questionId, String requestId, String intent)
            throws Exception {
        String body = json.writeValueAsString(
                new InterviewRoomService.IntentRequest(requestId, questionId, intent));
        return mvc.perform(post("/api/v1/interviews/{id}/requests", roomId)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("三种意图都有明确且互不相同的回应")
    void eachIntentGetsItsOwnResponse() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String questionId = room.path("questionId").asText();

        MvcResult clarify = request(id, questionId, "req-clarify-1", "CLARIFY");
        MvcResult replay = request(id, questionId, "req-replay-01", "REPLAY");
        MvcResult think = request(id, questionId, "req-think-001", "THINK");

        assertEquals(200, clarify.getResponse().getStatus());
        assertEquals(200, replay.getResponse().getStatus());
        assertEquals(200, think.getResponse().getStatus());

        assertEquals("CLARIFY", dataOf(clarify).path("turn").path("intent").asText());
        assertEquals("REPLAY", dataOf(replay).path("turn").path("intent").asText());
        assertEquals("THINK", dataOf(think).path("turn").path("intent").asText());

        String clarifyReply = dataOf(clarify).path("transcript").get(1).path("text").asText();
        String replayReply = dataOf(replay).path("transcript").get(3).path("text").asText();
        String thinkReply = dataOf(think).path("transcript").get(5).path("text").asText();

        assertFalse(clarifyReply.isBlank());
        assertFalse(replayReply.isBlank());
        assertFalse(thinkReply.isBlank());
        // 三种意图的回应互不相同，候选人能分辨
        assertNotEquals(clarifyReply, replayReply);
        assertNotEquals(replayReply, thinkReply);
        assertNotEquals(clarifyReply, thinkReply);

        // 重听会把题目原样再念一遍
        String prompt = room.path("stage").path("prompt").asText();
        assertTrue(replayReply.contains(prompt), "重听回应里应包含题目原文");
    }

    @Test
    @DisplayName("澄清与重听不算作已评分回答，回答数不变")
    void clarifyAndReplayAreNotAnswers() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String questionId = room.path("questionId").asText();

        request(id, questionId, "req-clarify-2", "CLARIFY");
        request(id, questionId, "req-replay-02", "REPLAY");
        request(id, questionId, "req-think-002", "THINK");

        mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(jsonPath("$.data.answers.length()").value(0))
                .andExpect(jsonPath("$.data.turns.length()").value(6));

        // 之后再提交一条真正的回答，才产生一条可评分记录
        String answerBody = json.writeValueAsString(
                new InterviewRoomService.TurnRequest("req-answer-01", questionId, "正式回答内容"));
        mvc.perform(post("/api/v1/interviews/{id}/turns", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(jsonPath("$.data.answers.length()").value(1))
                .andExpect(jsonPath("$.data.answers[0].text").value("正式回答内容"));
    }

    @Test
    @DisplayName("重复的意图请求不会重复记录")
    void repeatedIntentIsIdempotent() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String questionId = room.path("questionId").asText();

        request(id, questionId, "req-clarify-3", "CLARIFY");
        request(id, questionId, "req-clarify-3", "CLARIFY");

        mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(jsonPath("$.data.turns.length()").value(2));
    }

    @Test
    @DisplayName("不支持的请求类型被拒绝，非法标识被拒绝")
    void rejectsUnknownIntent() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String questionId = room.path("questionId").asText();

        assertEquals(400, request(id, questionId, "req-unknown-1", "SING").getResponse().getStatus());
        assertEquals(400, request(id, questionId, "short", "CLARIFY").getResponse().getStatus());
    }

    @Test
    @DisplayName("其余账号不能向他人房间发起请求")
    void refusesIntentOnForeignRoom() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String questionId = room.path("questionId").asText();

        String body = json.writeValueAsString(
                new InterviewRoomService.IntentRequest("req-foreign-02", questionId, "CLARIFY"));
        mvc.perform(post("/api/v1/interviews/{id}/requests", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, "user-c-other001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("页面停留在旧题目时发起请求会被拒绝")
    void refusesIntentForStaleQuestion() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();

        assertEquals(409, request(id, "stale-question", "req-stale-002", "CLARIFY")
                .getResponse().getStatus());
    }
}
