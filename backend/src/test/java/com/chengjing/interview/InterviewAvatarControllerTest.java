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
 * Owner: member C. C06 acceptance — at least three switchable interviewer looks, the camera is a
 * separate consent, and no frame can be uploaded through the consent endpoint.
 */
@SpringBootTest(classes = com.chengjing.app.ChengjingApplication.class)
@AutoConfigureMockMvc
class InterviewAvatarControllerTest {

    private static final String USER = "user-c-avatar01";
    /** A value that must never end up stored: it stands in for a captured frame. */
    private static final String VIDEO_MARKER = "UPLOAD_MUST_NOT_STORE_9f3a";

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
                        .content("{\"role\":\"产品经理\",\"minutes\":15}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode getRoom(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/interviews/{id}", id)
                .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private MvcResult putAvatar(String id, String avatarId) throws Exception {
        return mvc.perform(put("/api/v1/interviews/{id}/avatar", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InterviewRoomService.AvatarRequest(avatarId))))
                .andReturn();
    }

    private MvcResult putConsent(String id, String rawBody) throws Exception {
        return mvc.perform(put("/api/v1/interviews/{id}/video-consent", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andReturn();
    }

    @Test
    @DisplayName("至少有 3 个可切换的三维面试官形象")
    void offersAtLeastThreeAvatars() throws Exception {
        JsonNode room = newRoom();
        JsonNode avatars = room.path("avatars");

        assertTrue(avatars.size() >= 3, "至少要有 3 个形象可切换");
        avatars.forEach(look -> {
            assertFalse(look.path("id").asText().isBlank());
            assertFalse(look.path("name").asText().isBlank());
            assertFalse(look.path("accent").asText().isBlank(), "每个形象都有自己的配色");
            assertFalse(look.path("build").asText().isBlank());
        });
        assertEquals(InterviewAvatar.DEFAULT_ID, room.path("avatar").asText(),
                "未选择时使用默认形象");
    }

    @Test
    @DisplayName("切换形象后刷新仍然保持")
    void switchingAvatarPersists() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();

        String second = room.path("avatars").get(1).path("id").asText();
        assertEquals(200, putAvatar(id, second).getResponse().getStatus());
        assertEquals(second, getRoom(id).path("avatar").asText());

        String third = room.path("avatars").get(2).path("id").asText();
        putAvatar(id, third);
        assertEquals(third, getRoom(id).path("avatar").asText());
    }

    @Test
    @DisplayName("不存在的形象会被拒绝")
    void rejectsUnknownAvatar() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();

        assertEquals(400, putAvatar(id, "not-an-avatar").getResponse().getStatus());
        assertEquals(400, putAvatar(id, "").getResponse().getStatus());
        assertEquals(InterviewAvatar.DEFAULT_ID, getRoom(id).path("avatar").asText(), "拒绝后不改动");
    }

    @Test
    @DisplayName("摄像头默认未同意，单独同意后才开启，并且可以撤回")
    void cameraConsentIsSeparate() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();

        assertEquals(false, room.path("videoConsent").asBoolean(), "建房间不等于同意开摄像头");

        assertEquals(200, putConsent(id, "{\"granted\":true}").getResponse().getStatus());
        assertEquals(true, getRoom(id).path("videoConsent").asBoolean());

        assertEquals(200, putConsent(id, "{\"granted\":false}").getResponse().getStatus());
        assertEquals(false, getRoom(id).path("videoConsent").asBoolean(), "同意可以随时撤回");
    }

    @Test
    @DisplayName("同意接口只能记录布尔值，携带的视频内容不会被保存")
    void consentCannotCarryVideo() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();

        // 即使客户端硬塞一段“视频”，接口也没有字段可以接收它
        String body = "{\"granted\":true,\"video\":\"" + VIDEO_MARKER
                + "\",\"dataUrl\":\"data:video/webm;base64," + VIDEO_MARKER + "\"}";
        assertEquals(200, putConsent(id, body).getResponse().getStatus());

        MvcResult reopened = mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(status().isOk())
                .andReturn();
        String payload = reopened.getResponse().getContentAsString();

        assertFalse(payload.contains(VIDEO_MARKER), "房间里不能出现任何视频数据");
        assertEquals(true, getRoom(id).path("videoConsent").asBoolean(), "只记录了同意与否");
    }

    @Test
    @DisplayName("结束后不能切换形象")
    void cannotSwitchAvatarAfterFinish() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String second = room.path("avatars").get(1).path("id").asText();

        mvc.perform(post("/api/v1/interviews/{id}/finish", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(status().isOk());

        assertEquals(409, putAvatar(id, second).getResponse().getStatus());
        // 但摄像头同意仍可撤回，保证结束面试不是“卡在开着”的状态
        assertEquals(200, putConsent(id, "{\"granted\":false}").getResponse().getStatus());
    }

    @Test
    @DisplayName("其余账号不能切换他人房间的形象或写入同意")
    void refusesForeignRoom() throws Exception {
        JsonNode room = newRoom();
        String id = room.path("id").asText();
        String second = room.path("avatars").get(1).path("id").asText();

        mvc.perform(put("/api/v1/interviews/{id}/avatar", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, "user-c-other001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new InterviewRoomService.AvatarRequest(second))))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/interviews/{id}/video-consent", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, "user-c-other001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true}"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/interviews/{id}", id)
                        .header(RequestHeaderCurrentUserAdapter.HEADER, USER))
                .andExpect(jsonPath("$.data.videoConsent").value(false));
    }
}
