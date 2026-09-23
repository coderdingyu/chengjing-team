package com.chengjing.preparation;

import com.chengjing.shared.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 简历深挖节点接口：挂在本人计划下，别人的计划一律 403。 */
@RestController
@RequestMapping("/api/v1/preparation/plans/{planId}/nodes")
public class PlanNodeController {
    private final PlanNodeService service;

    public PlanNodeController(PlanNodeService service) {
        this.service = service;
    }

    public record GenerateNodesRequest(String focus, Boolean replace) {}

    public record SaveNodeRequest(String status, String content) {}

    @GetMapping("")
    public ApiResponse<List<PlanNodeService.NodeView>> list(@PathVariable String planId) {
        return ApiResponse.ok(service.list(planId));
    }

    @PostMapping("")
    public ApiResponse<List<PlanNodeService.NodeView>> generate(@PathVariable String planId,
            @RequestBody(required = false) GenerateNodesRequest request) {
        boolean replace = request != null && Boolean.TRUE.equals(request.replace());
        String focus = request == null ? null : request.focus();
        return ApiResponse.ok(service.generate(planId, focus, replace));
    }

    @PatchMapping("/{nodeId}")
    public ApiResponse<PlanNodeService.NodeView> save(@PathVariable String planId, @PathVariable String nodeId,
            @RequestBody SaveNodeRequest request) {
        return ApiResponse.ok(service.save(planId, nodeId, request.status(), request.content()));
    }

    @DeleteMapping("/{nodeId}")
    public ApiResponse<Map<String, String>> delete(@PathVariable String planId, @PathVariable String nodeId) {
        return ApiResponse.ok(Map.of("deletedId", service.delete(planId, nodeId)));
    }
}
