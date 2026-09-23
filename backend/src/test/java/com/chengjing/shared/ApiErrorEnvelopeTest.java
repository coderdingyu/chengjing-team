package com.chengjing.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chengjing.app.ChengjingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * docs/模块契约.md §1 asks for a meaningful HTTP status on failure. A client that calls the wrong
 * URL or the wrong method should be told so, rather than being handed a 500 that suggests the
 * server is broken.
 */
@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class ApiErrorEnvelopeTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("未知路径返回 404 而不是 500")
    void unknownRouteIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/no/such/endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("接口不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("已知路径上方法不匹配返回 405")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(post("/api/v1/system/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("该接口不支持此请求方法"));
    }

    @Test
    @DisplayName("失败返回与成功返回使用同一个 {success,data,message} 外壳")
    void failuresUseTheSharedEnvelope() throws Exception {
        mvc.perform(get("/api/v1/no/such/endpoint"))
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
