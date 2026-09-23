package com.chengjing.preparation;

import com.chengjing.shared.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 单题专项练习接口：题目从本人计划的节点发起，草稿与回答都只对本人开放。 */
@RestController
@RequestMapping("/api/v1/preparation")
public class PracticeController {
    private final PracticeService service;

    public PracticeController(PracticeService service) {
        this.service = service;
    }

    public record SaveDraftRequest(String draft) {}

    public record SubmitAnswerRequest(String text, String requestId) {}

    @PostMapping("/plans/{planId}/nodes/{nodeId}/practices")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PracticeService.PracticeView> create(@PathVariable String planId,
            @PathVariable String nodeId) {
        return ApiResponse.ok(service.create(planId, nodeId));
    }

    @GetMapping("/practices")
    public ApiResponse<List<PracticeService.PracticeView>> mine() {
        return ApiResponse.ok(service.mine());
    }

    @GetMapping("/practices/{practiceId}")
    public ApiResponse<PracticeService.PracticeView> detail(@PathVariable String practiceId) {
        return ApiResponse.ok(service.require(practiceId));
    }

    @PatchMapping("/practices/{practiceId}")
    public ApiResponse<PracticeService.PracticeView> saveDraft(@PathVariable String practiceId,
            @RequestBody SaveDraftRequest request) {
        return ApiResponse.ok(service.saveDraft(practiceId, request.draft()));
    }

    @PostMapping("/practices/{practiceId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PracticeService.PracticeView> answer(@PathVariable String practiceId,
            @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.ok(service.submit(practiceId, request.text(), request.requestId()));
    }

    @DeleteMapping("/practices/{practiceId}")
    public ApiResponse<Map<String, String>> delete(@PathVariable String practiceId) {
        return ApiResponse.ok(Map.of("deletedId", service.delete(practiceId)));
    }
}
