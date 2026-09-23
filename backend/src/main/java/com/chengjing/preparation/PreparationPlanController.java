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

/** 岗位准备计划接口：全部按当前账号归属返回，别人的计划一律拒绝。 */
@RestController
@RequestMapping("/api/v1/preparation/plans")
public class PreparationPlanController {
    private final PreparationPlanService service;

    public PreparationPlanController(PreparationPlanService service) {
        this.service = service;
    }

    public record CreatePlanRequest(String role, String title, String goal, String scenarioId, String jd) {}

    public record UpdatePlanRequest(String title, String goal, String status) {}

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PreparationPlanService.PlanView> create(@RequestBody CreatePlanRequest request) {
        return ApiResponse.ok(service.create(request.role(), request.title(), request.goal(),
                request.scenarioId(), request.jd()));
    }

    @GetMapping("")
    public ApiResponse<List<PreparationPlanService.PlanView>> mine() {
        return ApiResponse.ok(service.mine());
    }

    @GetMapping("/{planId}")
    public ApiResponse<PreparationPlanService.PlanView> detail(@PathVariable String planId) {
        return ApiResponse.ok(service.require(planId));
    }

    @PatchMapping("/{planId}")
    public ApiResponse<PreparationPlanService.PlanView> update(@PathVariable String planId,
            @RequestBody UpdatePlanRequest request) {
        return ApiResponse.ok(service.update(planId, request.title(), request.goal(), request.status()));
    }

    @DeleteMapping("/{planId}")
    public ApiResponse<Map<String, String>> delete(@PathVariable String planId) {
        return ApiResponse.ok(Map.of("deletedId", service.delete(planId)));
    }
}
