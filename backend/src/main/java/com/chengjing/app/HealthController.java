package com.chengjing.app;

import com.chengjing.shared.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class HealthController {
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "product", "chengjing-team"));
    }
}
