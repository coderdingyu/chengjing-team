package com.chengjing.assessment.rubric;

import com.chengjing.app.ChengjingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class ScoreRubricControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void exposesFourEvidenceOnlyDimensionsAndUncoveredRule() throws Exception {
        mvc.perform(get("/api/v1/assessments/rubric"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dimensions.length()").value(4))
                .andExpect(jsonPath("$.data.dimensions[0].key").value("QUESTION_GOAL"))
                .andExpect(jsonPath("$.data.dimensions[3].key").value("VALIDATION_REFLECTION"))
                .andExpect(jsonPath("$.data.scale[0].anchor").value(
                        "原话中存在可指出的明确错误；信息缺失不得记 0 分"))
                .andExpect(jsonPath("$.data.evidenceRules[1]").value(
                        "没有原文证据的维度标记为未覆盖，分数留空，不能填 0"));
    }
}
