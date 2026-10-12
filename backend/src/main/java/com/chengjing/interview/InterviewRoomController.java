package com.chengjing.interview;

import com.chengjing.shared.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner: member C (interview module) — API prefix {@code /api/v1/interviews} from the module contract.
 *
 * The caller is resolved through {@link CurrentUserPort}, never from a field in the request body, so
 * ownership cannot be forged by the browser.
 */
@RestController
@RequestMapping("/api/v1/interviews")
public class InterviewRoomController {

    private final InterviewRoomService service;

    public InterviewRoomController(InterviewRoomService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody InterviewRoomService.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/catalog")
    public ApiResponse<Map<String, Object>> catalog() {
        return ApiResponse.ok(service.catalog());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(service.mine());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> reopen(@PathVariable String id) {
        return ApiResponse.ok(service.reopen(id));
    }

    @PostMapping("/{id}/turns")
    public ApiResponse<Map<String, Object>> submitTurn(@PathVariable String id,
                                                       @RequestBody InterviewRoomService.TurnRequest request) {
        return ApiResponse.ok(service.submitTurn(id, request));
    }
}
