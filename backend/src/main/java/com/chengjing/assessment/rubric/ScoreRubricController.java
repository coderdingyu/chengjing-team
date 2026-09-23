package com.chengjing.assessment.rubric;

import com.chengjing.shared.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessments")
public class ScoreRubricController {
    private final ScoreRubricService rubricService;

    public ScoreRubricController(ScoreRubricService rubricService) {
        this.rubricService = rubricService;
    }

    @GetMapping("/rubric")
    public ApiResponse<ScoreRubric> rubric() {
        return ApiResponse.ok(rubricService.current());
    }
}
